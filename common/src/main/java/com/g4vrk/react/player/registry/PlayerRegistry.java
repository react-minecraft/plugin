package com.g4vrk.react.player.registry;

import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.player.registry.TrackedPlayerRegistry;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.registry.MapRegistry;
import com.g4vrk.react.registry.view.RegistryView;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The tracked players of the server, by unique id, behind the read-only
 * {@link TrackedPlayerRegistry} addons get.
 */
@Accessors(fluent = true)
public final class PlayerRegistry extends MapRegistry<UUID, TrackedPlayerImpl> {

    @Getter
    private final TrackedPlayerRegistry view = new View();

    @Override
    public void register(final @NotNull UUID key, final @NotNull TrackedPlayerImpl value) {
        final TrackedPlayerImpl previous = this.entries.put(key, value);

        if (previous != null) {
            previous.clearTransientState();
        }
    }

    @Override
    public @Nullable TrackedPlayerImpl unregister(final @NotNull UUID key) {
        final TrackedPlayerImpl removed = super.unregister(key);

        if (removed != null) {
            removed.clearTransientState();
        }

        return removed;
    }

    @Override
    public void clear() {
        this.entries.values().forEach(TrackedPlayerImpl::clearTransientState);
        super.clear();
    }

    /**
     * Finds the player whose entity has this id.
     *
     * @param entityId the entity id, as packets name it
     * @return the player, or {@code null} when no tracked player has this entity id
     */
    public @Nullable TrackedPlayerImpl byEntityId(final int entityId) {
        for (final TrackedPlayerImpl player : this.entries.values()) {
            if (player.bukkitPlayer.getEntityId() == entityId) {
                return player;
            }
        }

        return null;
    }

    /**
     * The read-only view of this registry.
     */
    private final class View extends RegistryView<UUID, TrackedPlayer, TrackedPlayerImpl>
            implements TrackedPlayerRegistry {

        private View() {
            super(PlayerRegistry.this);
        }

        @Override
        public @Nullable TrackedPlayer byEntityId(final int entityId) {
            return PlayerRegistry.this.byEntityId(entityId);
        }

    }

}
