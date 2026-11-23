package com.g4vrk.react.api.platform;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.UUID;

/**
 * Platform-independent facade over the hosting Minecraft server and React plugin container.
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface PlatformServer {

    /**
     * Returns the canonical lowercase platform identifier (for example {@code paper}, {@code folia},
     * {@code fabric}, or {@code minestom}).
     *
     * @return the platform identifier
     */
    @NotNull String platformName();

    /**
     * Returns React's plugin or mod name on this platform.
     *
     * @return the plugin name
     */
    @NotNull String pluginName();

    /**
     * Returns React's version string on this platform.
     *
     * @return the plugin version
     */
    @NotNull String pluginVersion();

    /**
     * Returns whether the hosting plugin is enabled.
     *
     * @return the plugin lifecycle state
     */
    boolean enabled();

    /**
     * Returns React's root data directory.
     *
     * @return the data directory
     */
    @NotNull Path dataDirectory();

    /**
     * Returns a stable, opaque fingerprint for this server installation.
     *
     * <p>The default fingerprint uses the platform id and normalized data directory. Platforms
     * with an established fingerprint should override this method to preserve it.</p>
     *
     * @return a lowercase SHA-256 fingerprint
     */
    default @NotNull String serverFingerprint() {

        final String identity = platformName() + "\n" + dataDirectory().toAbsolutePath().normalize();
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            final StringBuilder fingerprint = new StringBuilder(digest.length * 2);
            for (final byte value : digest) {
                fingerprint.append(Character.forDigit((value >>> 4) & 0x0F, 16));
                fingerprint.append(Character.forDigit(value & 0x0F, 16));
            }
            return fingerprint.toString();
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }

    }

    /**
     * Returns the native plugin or mod container object (for example {@code org.bukkit.plugin.Plugin}
     * on Bukkit-derived platforms).
     *
     * @return the native plugin handle
     */
    @NotNull Object pluginHandle();

    /**
     * Unwraps the native plugin or mod container when it is an instance of {@code type}.
     *
     * @param type the expected native type
     * @param <T>  the native type
     * @return the cast handle, or {@code null} when incompatible
     */
    default <T> @Nullable T unwrapPlugin(
            final @NotNull Class<T> type
    ) {

        final Object nativeHandle = pluginHandle();
        return type.isInstance(nativeHandle) ? type.cast(nativeHandle) : null;

    }

    /**
     * Dispatches a command as the server console.
     *
     * @param command the command line without a leading slash
     */
    void dispatchConsoleCommand(
            final @NotNull String command
    );

    /**
     * Adapts a native player handle without looking it up on the server.
     *
     * @param nativePlayer the platform's native player
     * @return a non-null platform player wrapper
     * @throws IllegalArgumentException if the handle is unsupported
     */
    default @NotNull PlatformPlayer adaptPlayer(final @NotNull Object nativePlayer) {

        throw new IllegalArgumentException("Unsupported native player: " + nativePlayer.getClass().getName());

    }

    /**
     * Captures the game state used by the Vector aim model. Call this from the player's owning
     * server thread; the default implementation provides no game snapshot.
     *
     * @param player          the tracked player
     * @param targetEntityId  the entity id last attacked, or {@code -1} when unknown
     * @return the snapshot, or {@code null} when the platform cannot provide one
     */
    default @Nullable AimSnapshot aimSnapshot(
            final @NotNull PlatformPlayer player,
            final int targetEntityId
    ) {

        return null;

    }

    /**
     * Looks up an online player by unique identifier.
     *
     * @param uniqueId the player UUID
     * @return the player, or {@code null} if offline
     */
    @Nullable PlatformPlayer player(
            final @NotNull UUID uniqueId
    );

    /**
     * Looks up an online player by exact name.
     *
     * @param name the player name
     * @return the player, or {@code null} if offline
     */
    @Nullable PlatformPlayer player(
            final @NotNull String name
    );

    /**
     * Returns a snapshot or view of currently connected players.
     *
     * @return the online players
     */
    @NotNull Collection<? extends PlatformPlayer> onlinePlayers();

}
