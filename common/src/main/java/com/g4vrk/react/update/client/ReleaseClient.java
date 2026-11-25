package com.g4vrk.react.update.client;

import com.g4vrk.react.update.UpdateChannel;

import com.g4vrk.react.update.checksum.ChecksumManifest;

import com.g4vrk.react.api.rcp.RCPAPI;
import com.g4vrk.react.util.HexCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
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
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]{0,39}");
    private static final Pattern BUILD_VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)(?:-[0-9a-f]{7,40})?");
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);
    private static final long MAX_METADATA_BYTES = 1024 * 1024;
    private static final long MAX_JAR_BYTES = 128L * 1024 * 1024;
    private static final String CHANNEL_RELEASE = "release";
    private static final String CHANNEL_NIGHTLY = "nightly";

    private final Logger logger;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    public ReleaseClient(
            final @NotNull Logger logger
    ) {

        this.logger = logger;

    }

    /**
     * A release whose {@code checksums.txt} passed the signature check.
     *
     * @param base            the download folder of the release, ending with {@code /}
     * @param checksums       the parsed file
     * @param revokedFallback {@code true} when this release was chosen as a revoked-version fallback
     */
    public record Release(
            @NotNull String base,
            @NotNull ChecksumManifest checksums,
            boolean revokedFallback
    ) {

        public Release(
                final @NotNull String base,
                final @NotNull ChecksumManifest checksums
        ) {

            this(base, checksums, false);

        }

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
            return bound(signed(releases + "download/v" + pinned + "/"), repository, CHANNEL_RELEASE, pinned);
        }

        final boolean nightly = channel == UpdateChannel.PRERELEASE;
        final Release release = bound(
                signed(releases + (nightly ? "download/nightly/" : "latest/download/")),
                repository,
                nightly ? CHANNEL_NIGHTLY : CHANNEL_RELEASE,
                null
        );
        if (!REACT_RCP.equalsIgnoreCase(repository)) {
            return release;
        }
        final String fallback = revokedVersions().get(release.version());
        if (fallback == null) {
            return release;
        }
        logger.warn("React RCP {} was revoked, using {} instead", release.version(), fallback);
        final Release fallbackRelease = bound(
                signed(releases + "download/v" + fallback + "/"),
                repository,
                CHANNEL_RELEASE,
                fallback
        );
        return new Release(fallbackRelease.base(), fallbackRelease.checksums(), true);

    }

    /**
     * Compares the numeric versions of releases and Git-commit-suffixed nightlies.
     * Other development versions are ignored.
     */
    public static boolean isReleaseDowngrade(
            final @NotNull String running,
            final @NotNull String candidate
    ) {

        final Matcher runningVersion = BUILD_VERSION.matcher(running);
        final Matcher candidateVersion = BUILD_VERSION.matcher(candidate);
        if (!runningVersion.matches() || !candidateVersion.matches()) {
            return false;
        }
        final String[] currentParts = runningVersion.group(1).split("\\.");
        final String[] nextParts = candidateVersion.group(1).split("\\.");
        final int length = Math.max(currentParts.length, nextParts.length);
        for (int index = 0; index < length; index++) {
            final String current = index < currentParts.length ? stripLeadingZeros(currentParts[index]) : "";
            final String next = index < nextParts.length ? stripLeadingZeros(nextParts[index]) : "";
            if (current.length() != next.length()) {
                return next.length() < current.length();
            }
            final int compare = next.compareTo(current);
            if (compare != 0) {
                return compare < 0;
            }
        }
        return false;

    }

    private static @NotNull String stripLeadingZeros(
            final @NotNull String digits
    ) {

        int index = 0;
        while (index < digits.length() && digits.charAt(index) == '0') {
            index++;
        }
        return digits.substring(index);

    }

    /**
     * Checks that a signed file belongs to the place it was fetched from, so a signed file of
     * another repository or release cannot be replayed there: another repo's file, a nightly as
     * the latest release, or an older release under a pinned tag.
     */
    public static @NotNull Release bound(
            final @NotNull Release release,
            final @NotNull String repository,
            final @NotNull String channel,
            final @Nullable String version
    ) throws VerificationException {

        final String signedRepository = release.checksums().header().get("repository");
        if (signedRepository == null || !signedRepository.equalsIgnoreCase(repository)) {
            throw new VerificationException("signed repository " + signedRepository + " is not " + repository);
        }
        final String signedChannel = release.checksums().header().get("channel");
        if (signedChannel == null || !signedChannel.equalsIgnoreCase(channel)) {
            throw new VerificationException("signed channel " + signedChannel + " is not " + channel);
        }
        final String signedVersion = release.checksums().version();
        if (version != null && signedVersion != null && !signedVersion.equals(version)) {
            throw new VerificationException("signed version " + signedVersion + " is not " + version);
        }
        return release;

    }

    /**
     * Downloads a jar of a release and checks its SHA-256. The file appears only once it is
     * complete and checked.
     *
     * @param release the release
     * @param jar     the jar name with its SHA-256, from {@link ChecksumManifest#jar(String)}
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
        final Path part = Files.createTempFile(target.toAbsolutePath().getParent(), target.getFileName() + ".", ".part");
        try {
            get(release.base() + jar.getKey(), part);
            if (!matches(part, jar.getValue())) {
                throw new VerificationException("SHA-256 of " + jar.getKey() + " does not match");
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException | InterruptedException | VerificationException failure) {
            try {
                Files.deleteIfExists(part);
            } catch (final IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }

    }

    /**
     * Whether a file has a SHA-256.
     *
     * @param file   the file; a missing file never matches
     * @param sha256 the hash in hex
     * @return {@code true} when it matches
     * @throws IOException when the file cannot be read
     */
    public static boolean matches(
            final @NotNull Path file,
            final @NotNull String sha256
    ) throws IOException {

        if (Files.notExists(file)) {
            return false;
        }
        try {
            final byte[] digest = digest(file);
            return MessageDigest.isEqual(digest, HexCodec.decode(sha256));
        } catch (final IllegalArgumentException ex) {
            return false;
        }

    }

    private @NotNull Release signed(
            final @NotNull String base
    ) throws IOException, InterruptedException, VerificationException {

        final byte[] bytes = get(base + CHECKSUMS);
        Ed25519SignatureVerifier.verify(bytes, get(base + SIGNATURE), CHECKSUMS);
        return new Release(base, ChecksumManifest.parse(new String(bytes, StandardCharsets.UTF_8)));

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

    private byte[] get(
            final @NotNull String url
    ) throws IOException, InterruptedException {

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "React-Updater")
                .GET()
                .build();
        final HttpResponse<byte[]> response = send(request, HttpResponse.BodySubscribers::ofByteArray, MAX_METADATA_BYTES, TIMEOUT);
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();

    }

    private void get(
            final @NotNull String url,
            final @NotNull Path target
    ) throws IOException, InterruptedException {

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "React-Updater")
                .GET()
                .build();
        final HttpResponse<Path> response = send(request, () -> HttpResponse.BodySubscribers.ofFile(target), MAX_JAR_BYTES, DOWNLOAD_TIMEOUT);
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }

    }

    private <T> @NotNull HttpResponse<T> send(
            final @NotNull HttpRequest request,
            final @NotNull Supplier<HttpResponse.BodySubscriber<T>> bodyFactory,
            final long limit,
            final @NotNull Duration timeout
    ) throws IOException, InterruptedException {

        final CompletableFuture<HttpResponse<T>> future = http.sendAsync(
                request,
                ignored -> new LimitedBodySubscriber<>(bodyFactory.get(), limit)
        );
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException failure) {
            future.cancel(true);
            throw new IOException("HTTP response timed out", failure);
        } catch (final InterruptedException failure) {
            future.cancel(true);
            throw failure;
        } catch (final ExecutionException failure) {
            throw new IOException("HTTP response failed", failure.getCause());
        }

    }

    private static byte[] digest(
            final @NotNull Path file
    ) throws IOException {

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }

        try (final InputStream input = Files.newInputStream(file)) {
            final byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
        }
        return digest.digest();

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
