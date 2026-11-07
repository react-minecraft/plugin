package com.g4vrk.react.api.rcp.player;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A player as the plugin tracks it, seen from an RCP.
 *
 * <p>Every method is safe to call from any thread.</p>
 */
@ApiStatus.AvailableSince("2.0")
public interface RCPPlayer {

    @NotNull UUID uniqueId();

    @NotNull String name();

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
     * The player's recent rotations, oldest first. The array is a copy.
     */
    @NotNull Rotation @NotNull [] rotations();

}
