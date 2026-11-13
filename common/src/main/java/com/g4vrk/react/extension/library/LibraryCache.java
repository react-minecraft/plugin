package com.g4vrk.react.extension.library;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Downloads and verifies pinned libraries before exposing them to a class loader.
 */
public final class LibraryCache {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Path resolve(final Path directory, final URI source, final String sha256) throws IOException, InterruptedException {

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
        if (Files.isRegularFile(cached) && sha256.equals(digest(cached))) {
            return cached;
        }

        final Path temporary = Files.createTempFile(directory, ".library-", ".tmp");
        try {
            final HttpRequest request = HttpRequest.newBuilder(source).timeout(Duration.ofSeconds(60)).GET().build();
            final HttpResponse<Path> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() != 200 || !sha256.equals(digest(temporary))) {
                throw new IOException("Could not verify library " + name + " (HTTP " + response.statusCode() + ')');
            }
            return Files.move(temporary, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }

    }

    private static String digest(final Path path) throws IOException {

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
        try (InputStream input = Files.newInputStream(path)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());

    }

}
