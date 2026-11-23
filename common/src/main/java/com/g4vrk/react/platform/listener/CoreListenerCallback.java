package com.g4vrk.react.platform.listener;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Pure-Java lifecycle and gameplay callbacks delivered by the active server platform into React's
 * core engine.
 */
public interface CoreListenerCallback {

    /**
     * Called when a player joins (or is already online during late startup).
     *
     * @param player the joining player
     */
    void onJoin(
            final @NotNull PlatformPlayer player
    );

    /**
     * Called when a player disconnects.
     *
     * @param player the disconnecting player
     */
    void onQuit(
            final @NotNull PlatformPlayer player
    );

    /**
     * Called when transient aim/combat state should be cleared (for example world change or respawn).
     *
     * @param playerId the player UUID
     */
    void onTransientReset(
            final @NotNull UUID playerId
    );

    /**
     * Called when a player hits a supported combat target.
     *
     * @param attackerId the attacking player UUID
     */
    void onCombatHit(
            final @NotNull UUID attackerId,
            final int targetEntityId
    );

    /**
     * Called when a rotation packet or look update is received for a player.
     *
     * @param playerId the player UUID
     * @param yaw      the reported yaw
     * @param pitch    the reported pitch
     */
    void onRotation(
            final @NotNull UUID playerId,
            final float yaw,
            final float pitch
    );

}
