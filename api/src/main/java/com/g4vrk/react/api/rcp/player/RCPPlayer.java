package com.g4vrk.react.api.rcp.player;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A player as the plugin tracks it, seen from an RCP.
 *
 * <p>The snapshot methods are safe to call from any thread. The {@link #bukkit()} handle must
 * only be used on the thread that owns the player's entity.</p>
 */
@ApiStatus.AvailableSince("2.0")
public interface RCPPlayer {

    @NotNull UUID uniqueId();

    @NotNull String name();

    /**
     * Gives the Bukkit player handle.
     *
     * <p>Use it only on the thread that owns the player's entity. This method may be called from
     * any thread, but most Bukkit operations on the returned player are not thread-safe.</p>
     *
     * @return the Bukkit player
     */
    @NotNull Player bukkit();

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
