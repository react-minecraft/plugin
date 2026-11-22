package com.g4vrk.react.api.rcp.player;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A player as the plugin tracks it, seen from an RCP.
 *
 * <p>The snapshot methods are safe to call from any thread. Operations on the native platform
 * handle obtained via {@link #platform()} or {@link #unwrap(Class)} must follow the threading
 * rules of the active server platform.</p>
 */
@ApiStatus.AvailableSince("2.0")
public interface RCPPlayer {

    @NotNull UUID uniqueId();

    @NotNull String name();

    /**
     * Returns the platform-independent player handle.
     *
     * @return the platform player
     */
    @NotNull PlatformPlayer platform();

    /**
     * Convenience shortcut for {@code platform().unwrap(type)}.
     *
     * @param type the expected native player type
     * @param <T>  the native player type
     * @return the native player, or {@code null} when running on a different platform
     */
    default <T> @Nullable T unwrap(
            final @NotNull Class<T> type
    ) {

        return platform().unwrap(type);

    }

    /**
     * Whether the player is in combat right now, by the plugin's own combat tracking.
     */
    boolean inCombat();

    /**
     * Starts or extends the player's combat window, as a hit on a supported target would.
     */
    void enterCombat();

    /**
     * Whether the plugin checks this player at all: false for players with a bypass,
     * in spectator mode and in the other cases the check skips.
     */
    boolean checked();

    /**
     * The player's recent rotations, oldest first, as a flat copy of
     * {@link RotationSample#STRIDE} floats per rotation. Use the accessors of
     * {@link RotationSample} to read it.
     */
    float @NotNull [] rotations();

}
