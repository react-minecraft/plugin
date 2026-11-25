package com.g4vrk.react.extension.library;

import com.g4vrk.react.util.HexCodec;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Downloads and verifies pinned libraries before exposing them to a class loader.
 */
public final class LibraryCache {

    private static final long MAX_LIBRARY_BYTES = 64L * 1024 * 1024;
    private static final Pattern FILENAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.+-]*\\.jar");
    private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.+-]{0,127}");
    private static final Pattern WINDOWS_DEVICE = Pattern.compile("(?:con|prn|aux|nul|com[0-9]|lpt[0-9])(?:\\..*)?", Pattern.CASE_INSENSITIVE);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public @NotNull Path resolve(
            final @NotNull Path dataDirectory,
            final @NotNull URI source,
            final @NotNull String sha256
    ) throws IOException, InterruptedException {

        if (!"https".equals(source.getScheme()) || source.getHost() == null || source.getUserInfo() != null
                || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected an HTTPS library URI and a lowercase SHA-256 digest");
        }
        final String path = source.getPath();
        final String name = path.substring(path.lastIndexOf('/') + 1);
        if (!FILENAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid library filename: " + name);
        }
        final Path directory = cacheDirectory(dataDirectory, source, sha256);
        final String cacheName = safeSegment(name) ? name : "library.jar";
        final Path cached = directory.resolve(cacheName);
        if (verified(cached, sha256)) {
            return cached;
        }

        final Path legacy = dataDirectory.resolve(".libraries").resolve(sha256 + '-' + name);
        final Path temporary = Files.createTempFile(directory, ".library-", ".tmp");
        try {
            if (Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS)) {
                final String legacyDigest;
                try (InputStream input = Files.newInputStream(legacy)) {
                    legacyDigest = copyBounded(input, temporary);
                }
                if (sha256.equals(legacyDigest)) {
                    return publish(temporary, cached, sha256);
                }
            }
            final HttpRequest request = HttpRequest.newBuilder(source).timeout(Duration.ofSeconds(60)).GET().build();
            final HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            final String actualDigest;
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("Could not verify library " + name + " (HTTP " + response.statusCode() + ')');
                }
                actualDigest = copyBounded(body, temporary);
            }
            if (!sha256.equals(actualDigest)) {
                throw new IOException("Could not verify library " + name + " (SHA-256 mismatch)");
            }
            return publish(temporary, cached, sha256);
        } finally {
            Files.deleteIfExists(temporary);
        }

    }

    /**
     * Mirrors repository paths and gives different pinned contents of the same URL separate directories.
     */
    private static @NotNull Path cacheDirectory(
            final @NotNull Path dataDirectory,
            final @NotNull URI source,
            final @NotNull String sha256
    ) throws IOException {

        final String host = source.getHost().toLowerCase(Locale.ROOT);
        final String[] segments = source.getRawPath().substring(1).split("/", -1);
        Path relative = Path.of("libraries", "_urls", sha256);
        if (safeSegment(host) && segments.length <= 16) {
            Path repository = Path.of("libraries", host);
            for (int index = 0; index < segments.length - 1; index++) {
                if (!safeSegment(segments[index])) {
                    repository = null;
                    break;
                }
                repository = repository.resolve(segments[index]);
            }
            if (repository != null) {
                relative = repository.resolve(sha256);
            }
        }
        Path directory = Files.createDirectories(dataDirectory);
        for (final Path segment : relative) {
            directory = directory.resolve(segment);
            if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try {
                Files.createDirectory(directory);
            } catch (final FileAlreadyExistsException existing) {
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    throw existing;
                }
            }
        }
        return directory;

    }

    private static boolean safeSegment(final @NotNull String name) {

        return PATH_SEGMENT.matcher(name).matches() && !name.endsWith(".") && !WINDOWS_DEVICE.matcher(name).matches();

    }

    private static @NotNull Path publish(
            final @NotNull Path temporary,
            final @NotNull Path cached,
            final @NotNull String sha256
    ) throws IOException {

        if (verified(cached, sha256)) {
            return cached;
        }
        try {
            try {
                return Files.move(temporary, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException unsupported) {
                return Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException failure) {
            if (verified(cached, sha256)) {
                return cached;
            }
            throw failure;
        }

    }

    private static boolean verified(
            final @NotNull Path cached,
            final @NotNull String sha256
    ) throws IOException {

        return Files.isRegularFile(cached, LinkOption.NOFOLLOW_LINKS) && sha256.equals(digest(cached));

    }

    private static String copyBounded(
            final InputStream input,
            final Path temporary
    ) throws IOException {

        final MessageDigest digest = sha256();
        long total = 0L;
        try (DigestInputStream hashing = new DigestInputStream(input, digest);
             OutputStream output = Files.newOutputStream(temporary)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = hashing.read(buffer)) != -1) {
                total += read;
                if (total > MAX_LIBRARY_BYTES) {
                    throw new IOException("Library exceeds maximum size of " + MAX_LIBRARY_BYTES + " bytes");
                }
                output.write(buffer, 0, read);
            }
        }
        return HexCodec.encode(digest.digest());

    }

    private static MessageDigest sha256() {

        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }

    }

    private static String digest(final Path path) throws IOException {

        final MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(path)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexCodec.encode(digest.digest());

    }

}
