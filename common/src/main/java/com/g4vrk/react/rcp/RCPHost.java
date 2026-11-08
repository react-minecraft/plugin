package com.g4vrk.react.rcp;

import com.g4vrk.react.api.rcp.event.RCPPlayerListener;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.registry.RCPPlayerRegistry;
import com.g4vrk.react.extension.HostedRCPEnvironment;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.registry.view.RegistryView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The plugin's side of every loaded RCP: passes player events to their listeners and keeps
 * their statuses.
 *
 * <p>The rotation path runs on netty threads for every movement packet, so it walks the
 * listeners directly instead of allocating a callback per event.</p>
 */
public final class RCPHost extends RegistryView<UUID, RCPPlayer, TrackedPlayerImpl> implements RCPPlayerRegistry {

    private final PlayerRegistry players;
    private final List<HostedRCPEnvironment> environments = new CopyOnWriteArrayList<>();

    public RCPHost(final @NotNull PlayerRegistry playerRegistry) {
        super(playerRegistry);
        this.players = playerRegistry;
    }

    public void attach(final @NotNull HostedRCPEnvironment environment) {
        this.environments.add(environment);
    }

    public void detach(final @NotNull HostedRCPEnvironment environment) {
        this.environments.remove(environment);
    }

    @Override
    public @Nullable RCPPlayer byEntityId(final int entityId) {
        return this.players.byEntityId(entityId);
    }

    public void rotation(final @NotNull TrackedPlayerImpl player, final float @NotNull [] sample) {
        final List<HostedRCPEnvironment> attached = this.environments;

        for (int i = 0; i < attached.size(); i++) {
            final HostedRCPEnvironment environment = attached.get(i);

            for (final RCPPlayerListener listener : environment.playerListeners()) {
                try {
                    listener.onRotation(player, sample);
                } catch (final Throwable failure) {
                    environment.logger().warn("A player listener of RCP '{}' failed", environment.name(), failure);
                }
            }
        }
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
        for (final HostedRCPEnvironment environment : this.environments) {
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
        return List.copyOf(this.environments);
    }

    public @Nullable String status() {
        for (final HostedRCPEnvironment environment : this.environments) {
            final String status = environment.currentStatus();
            if (status != null) {
                return status;
            }
        }
        return null;
    }

}
