package com.g4vrk.react.api.rcp.player.registry;

import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The online players an RCP may inspect, by unique id.
 *
 * <p>An entry disappears when its player quits.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface RCPPlayerRegistry extends Registry<UUID, RCPPlayer> {

    /**
     * Finds the player whose entity has this id, as packets name it.
     *
     * @param entityId the entity id
     * @return the player, or {@code null} when no online player has this entity id
     */
    @Nullable RCPPlayer byEntityId(final int entityId);

}
