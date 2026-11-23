package com.g4vrk.react.bukkit.util;

import org.bukkit.Server;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Preserves the existing Bukkit server fingerprint used to encrypt linked RCP credentials.
 */
public final class BukkitServerFingerprint {

    private static final List<Path> MACHINE_ID_FILES = List.of(
            Path.of("/etc/machine-id"),
            Path.of("/var/lib/dbus/machine-id")
    );

    private BukkitServerFingerprint() {

    }

    public static @NotNull String of(final @NotNull Server server) {

        final List<World> worlds = server.getWorlds();
        final String world = worlds.isEmpty() ? "" : worlds.get(0).getUID().toString();
        return hash(world + "\n" + server.getPort() + "\n" + machineId());

    }

    private static @NotNull String hash(final @NotNull String value) {

        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder result = new StringBuilder(digest.length * 2);
            for (final byte element : digest) {
                result.append(Character.forDigit((element >>> 4) & 0x0F, 16));
                result.append(Character.forDigit(element & 0x0F, 16));
            }
            return result.toString();
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }

    }

    private static @NotNull String machineId() {

        for (final Path file : MACHINE_ID_FILES) {
            try {
                final String id = Files.readString(file).trim();
                if (!id.isEmpty()) {
                    return id;
                }
            } catch (final IOException | SecurityException ignored) {
                /*
                 * The next source is tried.
                 */
            }
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final IOException exception) {
            return "unknown";
        }

    }

}
