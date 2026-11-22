package com.g4vrk.react.api.platform.player;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Platform-independent representation of a connected player.
 *
 * <p>Equality and hashing are defined by {@link #uniqueId()} so that players remain stable keys
 * across channel subscriptions, registries, and command executions.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface PlatformPlayer extends PlatformSender {

    /**
     * Returns the player's unique identifier.
     *
     * @return the player UUID
     */
    @NotNull UUID uniqueId();

    /**
     * Returns the server-assigned entity id used by network packets.
     *
     * @return the entity id
     */
    int entityId();

    /**
     * Checks whether the player is currently connected to the server.
     *
     * @return {@code true} while online
     */
    boolean online();

    @Override
    default boolean isPlayer() {

        return true;

    }

    @Override
    default @NotNull PlatformPlayer asPlayer() {

        return this;

    }

}
