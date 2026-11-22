package com.g4vrk.react.api.platform.sender;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-independent representation of a command sender or message recipient.
 *
 * <p>Implementations bridge the active server platform (Paper, Folia, Fabric, Minestom, etc.)
 * into React's pure-Java core and extension API. Platform-specific extensions may retrieve the
 * underlying native handle via {@link #unwrap(Class)}.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface PlatformSender extends Audience {

    /**
     * Returns the display or console name of this sender.
     *
     * @return the sender name
     */
    @NotNull String name();

    /**
     * Checks whether this sender holds the given permission node.
     *
     * @param permission the permission node
     * @return {@code true} if the sender has the permission
     */
    boolean hasPermission(
            final @NotNull String permission
    );

    /**
     * Checks whether this sender represents an online player.
     *
     * @return {@code true} when backed by a {@link PlatformPlayer}
     */
    default boolean isPlayer() {

        return this instanceof PlatformPlayer;

    }

    /**
     * Returns this sender as a {@link PlatformPlayer}, or {@code null} for non-player senders
     * such as the server console.
     *
     * @return the player view, or {@code null}
     */
    default @Nullable PlatformPlayer asPlayer() {

        return this instanceof final PlatformPlayer player ? player : null;

    }

    /**
     * Returns the native platform object backing this sender.
     *
     * @return the native handle
     */
    @NotNull Object handle();

    /**
     * Unwraps the native platform handle when it is an instance of {@code type}.
     *
     * @param type the expected platform type
     * @param <T>  the native type
     * @return the cast handle, or {@code null} when incompatible
     */
    default <T> @Nullable T unwrap(
            final @NotNull Class<T> type
    ) {

        final Object nativeHandle = handle();
        return type.isInstance(nativeHandle) ? type.cast(nativeHandle) : null;

    }

}
