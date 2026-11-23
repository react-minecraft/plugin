package com.g4vrk.react.extension.library;

import com.g4vrk.react.util.HexCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * Downloads and verifies pinned libraries before exposing them to a class loader.
 */
public final class LibraryCache {

    private static final long MAX_LIBRARY_BYTES = 64L * 1024 * 1024;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Path resolve(
            final Path directory,
            final URI source,
            final String sha256
    ) throws IOException, InterruptedException {

        if (!"https".equals(source.getScheme()) || source.getHost() == null || source.getUserInfo() != null
                || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected an HTTPS library URI and a lowercase SHA-256 digest");
        }
        final String path = source.getPath();
        final String name = path.substring(path.lastIndexOf('/') + 1);
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.+-]*\\.jar")) {
            throw new IllegalArgumentException("Invalid library filename: " + name);
        }
        Files.createDirectories(directory);
        final Path cached = directory.resolve(sha256 + '-' + name);
        if (Files.isRegularFile(cached, LinkOption.NOFOLLOW_LINKS) && sha256.equals(digest(cached))) {
            return cached;
        }

        final Path temporary = Files.createTempFile(directory, ".library-", ".tmp");
        try {
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
            try {
                return Files.move(temporary, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException unsupported) {
                return Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

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
