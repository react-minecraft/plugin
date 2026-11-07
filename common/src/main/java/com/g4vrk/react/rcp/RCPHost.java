package com.g4vrk.react.rcp;

import com.g4vrk.react.api.rcp.event.RCPPlayerListener;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.RCPPlayers;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.g4vrk.react.extension.HostedRCPEnvironment;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.registry.PlayerRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The plugin's side of every loaded RCP: passes player events to their listeners and keeps
 * their statuses.
 */
public final class RCPHost implements RCPPlayers {

    private final PlayerRegistry playerRegistry;
    private final List<HostedRCPEnvironment> environments = new CopyOnWriteArrayList<>();

    public RCPHost(final @NotNull PlayerRegistry playerRegistry) {
        this.playerRegistry = playerRegistry;
    }

    public void attach(final @NotNull HostedRCPEnvironment environment) {
        environments.add(environment);
    }

    public void detach(final @NotNull HostedRCPEnvironment environment) {
        environments.remove(environment);
    }

    @Override
    public @Nullable RCPPlayer get(final @NotNull UUID uniqueId) {
        return playerRegistry.get(uniqueId);
    }

    @Override
    public @Nullable RCPPlayer byEntityId(final int entityId) {
        return playerRegistry.byEntityId(entityId);
    }

    @Override
    public @NotNull Collection<? extends RCPPlayer> online() {
        return playerRegistry.values();
    }

    public void rotation(final @NotNull TrackedPlayerImpl player, final @NotNull Rotation rotation) {
        dispatch(listener -> listener.onRotation(player, rotation));
    }

    public void reload(final @NotNull TrackedPlayerImpl player) {
        dispatch(listener -> listener.onReload(player));
    }

    public void reset(final @NotNull UUID player) {
        dispatch(listener -> listener.onReset(player));
    }

    public void quit(final @NotNull UUID player) {
        dispatch(listener -> listener.onQuit(player));
    }

    private void dispatch(final @NotNull Consumer<RCPPlayerListener> event) {
        for (final HostedRCPEnvironment environment : environments) {
            for (final RCPPlayerListener listener : environment.playerListeners()) {
                try {
                    event.accept(listener);
                } catch (final Throwable failure) {
                    environment.logger().warn("A player listener of RCP '{}' failed", environment.name(), failure);
                }
            }
        }
    }

    public @NotNull List<HostedRCPEnvironment> environments() {
        return List.copyOf(environments);
    }

    public @Nullable String status() {
        for (final HostedRCPEnvironment environment : environments) {
            final String status = environment.currentStatus();
            if (status != null) {
                return status;
            }
        }
        return null;
    }

}
