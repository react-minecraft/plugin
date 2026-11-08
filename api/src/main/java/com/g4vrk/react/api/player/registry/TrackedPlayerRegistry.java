package com.g4vrk.react.api.player.registry;

import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Online players the plugin tracks, by unique id.
 *
 * <p>An entry disappears when its player quits.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface TrackedPlayerRegistry extends Registry<UUID, TrackedPlayer> {

    /**
     * Finds the player whose entity has this id, as packets name it.
     *
     * @param entityId the entity id
     * @return the player, or {@code null} when no tracked player has this entity id
     */
    @Nullable TrackedPlayer byEntityId(final int entityId);

}
