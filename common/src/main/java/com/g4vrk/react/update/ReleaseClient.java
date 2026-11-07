package com.g4vrk.react.update;

import com.g4vrk.react.api.rcp.RCPAPI;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

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
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads releases of the react-minecraft repositories from GitHub.
 *
 * <p>Files come from direct release links, not the GitHub API, so there is no rate limit.
 * {@code checksums.txt} is checked against the organization's ed25519 key and every jar
 * against its SHA-256.</p>
 */
public final class ReleaseClient {

    /**
     * The repository of the React RCP, the only one the backend can revoke releases of.
     */
    public static final String REACT_RCP = "react-minecraft/rcp";

    private static final String CHECKSUMS = "checksums.txt";
    private static final String SIGNATURE = CHECKSUMS + ".sig";
    private static final String REVOKED_URL = RCPAPI.API_URL + "/api/v1/rcp/revoked";
    private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAQ1cusiIHlnREUXw/ubE9IqSkXBxYPmZYNnxn58Jpo8g=";
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]{0,39}");
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final Logger logger;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    public ReleaseClient(final @NotNull Logger logger) {
        this.logger = logger;
    }

    /**
     * A release whose {@code checksums.txt} passed the signature check.
     *
     * @param base      the download folder of the release, ending with {@code /}
     * @param checksums the parsed file
     */
    public record Release(@NotNull String base, @NotNull Checksums checksums) {

        /**
         * The version of the release.
         *
         * @return the version, {@code ?} when the header has none
         */
        public @NotNull String version() {
            final String version = checksums.version();
            return version == null ? "?" : version;
        }

    }

    /**
     * Fetches the release a component follows. A revoked React RCP release is swapped for
     * the release the backend names, unless a version is pinned.
     *
     * @param repository the repository, {@code owner/name}
     * @param channel    the channel
     * @param pinned     a pinned version, or {@code null}
     * @return the release
     * @throws IOException           when GitHub cannot be reached
     * @throws InterruptedException  when the thread is interrupted
     * @throws VerificationException when the signature or the version is bad
     */
    public @NotNull Release release(
            final @NotNull String repository,
            final @NotNull UpdateChannel channel,
            final @Nullable String pinned
    ) throws IOException, InterruptedException, VerificationException {
        if (!REPOSITORY.matcher(repository).matches()) {
            throw new VerificationException("bad repository name " + repository);
        }
        if (pinned != null && !VERSION.matcher(pinned).matches()) {
            throw new VerificationException("bad pinned version " + pinned);
        }

        final String releases = "https://github.com/" + repository + "/releases/";
        if (pinned != null) {
            return signed(releases + "download/v" + pinned + "/");
        }

        final Release release = signed(releases + (channel == UpdateChannel.PRERELEASE ? "download/nightly/" : "latest/download/"));
        if (!REACT_RCP.equalsIgnoreCase(repository)) {
            return release;
        }
        final String fallback = revokedVersions().get(release.version());
        if (fallback == null) {
            return release;
        }
        logger.warn("React RCP {} was revoked, using {} instead", release.version(), fallback);
        return signed(releases + "download/v" + fallback + "/");
    }

    /**
     * Downloads a jar of a release and checks its SHA-256. The file appears only once it is
     * complete and checked.
     *
     * @param release the release
     * @param jar     the jar name with its SHA-256, from {@link Checksums#jar(String)}
     * @param target  where the jar goes
     * @throws IOException           when the download fails
     * @throws InterruptedException  when the thread is interrupted
     * @throws VerificationException when the hash does not match
     */
    public void download(
            final @NotNull Release release,
            final Map.@NotNull Entry<String, String> jar,
            final @NotNull Path target
    ) throws IOException, InterruptedException, VerificationException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        final Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.write(part, get(release.base() + jar.getKey()));
        if (!matches(part, jar.getValue())) {
            Files.deleteIfExists(part);
            throw new VerificationException("SHA-256 of " + jar.getKey() + " does not match");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Whether a file has a SHA-256.
     *
     * @param file   the file; a missing file never matches
     * @param sha256 the hash in hex
     * @return {@code true} when it matches
     * @throws IOException when the file cannot be read
     */
    public static boolean matches(final @NotNull Path file, final @NotNull String sha256) throws IOException {
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

    private @NotNull Release signed(final @NotNull String base)
            throws IOException, InterruptedException, VerificationException {
        final byte[] bytes = get(base + CHECKSUMS);
        verifySignature(bytes, get(base + SIGNATURE));
        return new Release(base, Checksums.parse(new String(bytes, StandardCharsets.UTF_8)));
    }

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

    private byte[] get(final @NotNull String url) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "React-Updater")
                .GET()
                .build();
        final HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
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

    /**
     * A release that failed a check.
     */
    public static final class VerificationException extends Exception {

        public VerificationException(final @NotNull String message) {
            super(message);
        }

    }

}
