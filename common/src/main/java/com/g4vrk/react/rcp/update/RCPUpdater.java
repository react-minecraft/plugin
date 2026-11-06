package com.g4vrk.react.rcp.update;

import com.g4vrk.react.api.config.ConfigFile;
import com.g4vrk.react.api.rcp.RCPApi;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Keeps the React RCP in {@code .rcp/providers} up to date with its GitHub releases.
 *
 * <p>Files come from direct release links, not the GitHub API, so there is no rate limit.
 * {@code checksums.txt} is checked against the built-in ed25519 key and the jar against
 * its SHA-256. The last good download is kept in {@code .rcp/.cache} and is used when
 * GitHub cannot be reached or when {@code auto-update} is off.</p>
 *
 * <p>The React backend may revoke a broken release: then the release it names as the
 * fallback is installed instead.</p>
 */
public final class RCPUpdater {

    private static final String REPOSITORY = "react-minecraft/rcp";
    private static final String JAR_PREFIX = "react-inference-rcp";
    private static final String INSTALLED_JAR = JAR_PREFIX + ".jar";
    private static final String CHECKSUMS = "checksums.txt";
    private static final String SIGNATURE = CHECKSUMS + ".sig";

    private static final String REVOKED_URL = RCPApi.API_URL + "/api/v1/rcp/revoked";

    private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAQ1cusiIHlnREUXw/ubE9IqSkXBxYPmZYNnxn58Jpo8g=";

    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]{0,39}");

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final Path providers;
    private final Path cache;
    private final ConfigFile<RCPSettings> settingsFile;
    private final Logger logger;
    private final HttpClient http;

    private volatile Status status = new Status(null, RCPSettings.Channel.RELEASE, null, "not checked");

    public RCPUpdater(
            final @NotNull Path rcpDirectory,
            final @NotNull Path providers,
            final @NotNull String language,
            final @NotNull Logger logger
    ) {
        this.providers = providers;
        this.cache = rcpDirectory.resolve(".cache");
        this.logger = logger;
        this.settingsFile = ConfigFile.builder(RCPSettings.class)
                .path(rcpDirectory.resolve("react-rcp.yml"))
                .defaults(RCPUpdater.class.getClassLoader(), language + "/react-rcp.yml")
                .logger(logger)
                .build();
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * What the last {@link #update()} did, for {@code /react rcps}.
     *
     * @param version   the installed version, or null when none is installed
     * @param lastCheck when GitHub last answered, or null
     * @param state     a short word on the outcome
     */
    public record Status(
            @Nullable String version,
            @NotNull RCPSettings.Channel channel,
            @Nullable Instant lastCheck,
            @NotNull String state
    ) {
    }

    public @NotNull Status status() {
        return status;
    }

    /**
     * Brings {@code .rcp/providers/react-inference-rcp.jar} in line with the settings.
     *
     * <p>Blocks on the network, so it runs off the main thread. Never throws: a failure
     * leaves the server without the RCP or on the cached one.</p>
     */
    public void update() {
        final RCPSettings.Settings settings;
        try {
            settings = settingsFile.load().settings();
        } catch (final ConfigurateException ex) {
            logger.error("Could not read {}, the React RCP is not loaded", settingsFile.path(), ex);
            uninstall();
            status = new Status(null, RCPSettings.Channel.RELEASE, null, "bad config");
            return;
        }

        final RCPSettings.Channel channel = settings.channel() == null ? RCPSettings.Channel.RELEASE : settings.channel();
        if (!settings.enabled()) {
            uninstall();
            status = new Status(null, channel, null, "disabled");
            return;
        }

        try {
            Files.createDirectories(cache);
            Files.createDirectories(providers);
        } catch (final IOException ex) {
            logger.error("Could not create {}", cache, ex);
            return;
        }

        final Checksums cached = cachedChecksums();
        if (!settings.autoUpdate() && cached != null) {
            install(cached, channel, null, "cached");
            return;
        }

        try {
            final Checksums latest = fetch(channel, cached);
            install(latest, channel, Instant.now(), "up to date");
        } catch (final IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (cached != null) {
                logger.warn("GitHub is unreachable ({}), loading the cached React RCP", ex.getMessage());
                install(cached, channel, null, "offline");
            } else {
                logger.warn("GitHub is unreachable ({}) and no React RCP is cached, running without it", ex.getMessage());
                status = new Status(null, channel, null, "offline");
            }
        } catch (final VerificationException ex) {
            logger.warn("The React RCP update was rejected: {}", ex.getMessage());
            if (cached != null) {
                install(cached, channel, null, "rejected update");
            } else {
                status = new Status(null, channel, null, "rejected");
            }
        }
    }

    private @NotNull Checksums fetch(
            final @NotNull RCPSettings.Channel channel,
            final @Nullable Checksums cached
    ) throws IOException, InterruptedException, VerificationException {
        final String releases = "https://github.com/" + REPOSITORY + "/releases/";
        final CompletableFuture<Map<String, String>> revoked = CompletableFuture.supplyAsync(this::revokedVersions);
        Signed signed = signed(releases + (channel == RCPSettings.Channel.ALL ? "download/nightly/" : "latest/download/"));

        final String fallback = revoked.join().get(signed.checksums().version());
        if (fallback != null) {
            logger.warn("React RCP {} was revoked, installing {} instead", signed.checksums().version(), fallback);
            signed = signed(releases + "download/v" + fallback + "/");
        }

        final Checksums checksums = signed.checksums();
        final Map.Entry<String, String> jar = usableJar(checksums);
        final Path cachedJar = cache.resolve(INSTALLED_JAR);
        if (cached == null || !jar.equals(cached.jar(JAR_PREFIX))) {
            final Path download = cache.resolve(INSTALLED_JAR + ".part");
            Files.write(download, get(signed.base() + jar.getKey()));
            if (!matches(download, jar.getValue())) {
                Files.deleteIfExists(download);
                throw new VerificationException("SHA-256 of " + jar.getKey() + " does not match");
            }
            Files.move(download, cachedJar, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Downloaded React RCP {} ({})", checksums.version(), channel.name().toLowerCase(Locale.ROOT));
        }

        Files.write(cache.resolve(CHECKSUMS), signed.bytes());
        Files.write(cache.resolve(SIGNATURE), signed.signature());
        return checksums;
    }

    private record Signed(String base, byte[] bytes, byte[] signature, Checksums checksums) {
    }

    private @NotNull Signed signed(final @NotNull String base)
            throws IOException, InterruptedException, VerificationException {
        final byte[] bytes = get(base + CHECKSUMS);
        final byte[] signature = get(base + SIGNATURE);
        verifySignature(bytes, signature);
        return new Signed(base, bytes, signature, Checksums.parse(new String(bytes, StandardCharsets.UTF_8)));
    }

    private static @NotNull Map.Entry<String, String> usableJar(final @NotNull Checksums checksums)
            throws VerificationException {
        if (checksums.apiVersion() != RCPApi.VERSION) {
            throw new VerificationException("the build needs api-version " + checksums.apiVersion()
                    + ", this plugin provides " + RCPApi.VERSION + ", update the plugin");
        }
        final Map.Entry<String, String> jar = checksums.jar(JAR_PREFIX);
        if (jar == null) {
            throw new VerificationException(CHECKSUMS + " lists no " + JAR_PREFIX + " jar");
        }
        return jar;
    }

    /**
     * @return each revoked version with its fallback; empty when the backend cannot be reached,
     * so an outage never blocks an update
     */
    private @NotNull Map<String, String> revokedVersions() {
        try {
            final JsonElement revoked = JsonParser.parseString(new String(get(REVOKED_URL), StandardCharsets.UTF_8))
                    .getAsJsonObject().get("revoked");
            if (revoked == null || !revoked.isJsonObject()) {
                return Map.of();
            }
            final Map<String, String> versions = new HashMap<>();
            for (final Map.Entry<String, JsonElement> entry : revoked.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive() && VERSION.matcher(entry.getValue().getAsString()).matches()) {
                    versions.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return versions;
        } catch (final IOException | JsonParseException | IllegalStateException ex) {
            logger.debug("Could not read the revoked React RCP versions", ex);
            return Map.of();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Map.of();
        }
    }

    private @Nullable Checksums cachedChecksums() {
        final Path file = cache.resolve(CHECKSUMS);
        final Path jarFile = cache.resolve(INSTALLED_JAR);
        if (Files.notExists(file) || Files.notExists(jarFile)) {
            return null;
        }
        try {
            final byte[] bytes = Files.readAllBytes(file);
            verifySignature(bytes, Files.readAllBytes(cache.resolve(SIGNATURE)));
            final Checksums checksums = Checksums.parse(new String(bytes, StandardCharsets.UTF_8));
            if (!matches(jarFile, usableJar(checksums).getValue())) {
                Files.deleteIfExists(jarFile);
                return null;
            }
            return checksums;
        } catch (final IOException | VerificationException ex) {
            logger.warn("The cached React RCP is unusable ({}), it will be downloaded again", ex.getMessage());
            return null;
        }
    }

    private void install(
            final @NotNull Checksums checksums,
            final @NotNull RCPSettings.Channel channel,
            final @Nullable Instant checked,
            final @NotNull String state
    ) {
        final Instant lastCheck = checked != null ? checked : status.lastCheck();
        try {
            Files.copy(cache.resolve(INSTALLED_JAR), providers.resolve(INSTALLED_JAR), StandardCopyOption.REPLACE_EXISTING);
            status = new Status(checksums.version(), channel, lastCheck, state);
        } catch (final IOException ex) {
            logger.error("Could not install the React RCP into {}", providers, ex);
            status = new Status(null, channel, lastCheck, "install failed");
        }
    }

    private void uninstall() {
        try {
            Files.deleteIfExists(providers.resolve(INSTALLED_JAR));
        } catch (final IOException ex) {
            logger.warn("Could not remove {}", providers.resolve(INSTALLED_JAR), ex);
        }
    }

    private byte[] get(final @NotNull String url) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "React-RCP-Updater")
                .GET()
                .build();
        final HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private static boolean matches(final @NotNull Path file, final @NotNull String sha256) throws IOException {
        if (Files.notExists(file)) {
            return false;
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return MessageDigest.isEqual(digest, HexFormat.of().parseHex(sha256));
        } catch (final GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }

    private static void verifySignature(final byte[] data, final byte[] signatureText) throws VerificationException {
        final boolean valid;
        try {
            final PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
            final Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(data);
            valid = verifier.verify(Base64.getDecoder().decode(new String(signatureText, StandardCharsets.US_ASCII).strip()));
        } catch (final GeneralSecurityException | IllegalArgumentException ex) {
            throw new VerificationException("unreadable ed25519 signature on " + CHECKSUMS);
        }
        if (!valid) {
            throw new VerificationException("bad ed25519 signature on " + CHECKSUMS);
        }
    }

    private static final class VerificationException extends Exception {

        private VerificationException(final @NotNull String message) {
            super(message);
        }

    }

}
