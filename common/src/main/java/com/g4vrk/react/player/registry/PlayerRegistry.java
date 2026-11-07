package com.g4vrk.react.player.registry;

import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.player.TrackedPlayerRegistry;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.registry.MapRegistry;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Accessors(fluent = true)
public final class PlayerRegistry extends MapRegistry<UUID, TrackedPlayerImpl> {

    @Getter
    private final TrackedPlayerRegistry view = new View();

    @Override
    public @Nullable TrackedPlayerImpl register(final @NotNull UUID key, final @NotNull TrackedPlayerImpl value) {
        final TrackedPlayerImpl previous = super.register(key, value);
        if (previous != null) {
            previous.clearTransientState();
        }
        return previous;
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
        entries.values().forEach(TrackedPlayerImpl::clearTransientState);
        super.clear();
    }

    public @Nullable TrackedPlayerImpl byEntityId(final int entityId) {
        for (final TrackedPlayerImpl player : entries.values()) {
            if (player.bukkitPlayer.getEntityId() == entityId) {
                return player;
            }
        }
        return null;
    }

    private final class View implements TrackedPlayerRegistry {

        @Override
        public @Nullable TrackedPlayer get(final @NotNull UUID key) {
            return PlayerRegistry.this.get(key);
        }

        @Override
        public @NotNull Collection<TrackedPlayer> values() {
            return List.copyOf(entries.values());
        }

        @Override
        public int size() {
            return PlayerRegistry.this.size();
        }

        @Override
        public @Nullable TrackedPlayer byEntityId(final int entityId) {
            return PlayerRegistry.this.byEntityId(entityId);
        }

    }

}
