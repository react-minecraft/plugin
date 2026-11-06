package com.g4vrk.react.rcp;

import com.g4vrk.react.api.rcp.command.RCPCommand;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.event.RCPPlayerListener;
import com.g4vrk.react.api.rcp.internal.environment.RCPEnvironmentFactory;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.RCPPlayers;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The plugin's side of every loaded RCP: builds their environments, passes player events
 * to their listeners and keeps their commands and statuses.
 */
public final class RCPHost implements RCPEnvironmentFactory, RCPPlayers {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final PlayerRegistry playerRegistry;
    private final Logger logger;

    private final List<HostedEnvironment> environments = new CopyOnWriteArrayList<>();

    public RCPHost(
            final @NotNull Plugin plugin,
            final @NotNull Scheduler scheduler,
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull Logger logger
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.playerRegistry = playerRegistry;
        this.logger = logger;
    }

    @Override
    public @NotNull RCPEnvironment create(
            final @NotNull RCPMetadata metadata,
            final @NotNull Path directory,
            final @NotNull Logger rcpLogger
    ) {
        final HostedEnvironment environment = new HostedEnvironment(this, metadata, directory, rcpLogger);
        environments.add(environment);
        return environment;
    }

    @Override
    public void release(final @NotNull RCPEnvironment environment) {
        if (environment instanceof HostedEnvironment hosted && environments.remove(hosted)) {
            hosted.release();
        }
    }

    @Override
    public @Nullable RCPPlayer get(final @NotNull UUID uniqueId) {
        return playerRegistry.getPlayer(uniqueId);
    }

    @Override
    public @Nullable RCPPlayer byEntityId(final int entityId) {
        return playerRegistry.getByEntityId(entityId);
    }

    @Override
    public @NotNull Collection<? extends RCPPlayer> online() {
        return playerRegistry.all();
    }

    public void rotation(final @NotNull ReactPlayer player, final @NotNull Rotation rotation) {
        dispatch(listener -> listener.onRotation(player, rotation));
    }

    public void reload(final @NotNull ReactPlayer player) {
        dispatch(listener -> listener.onReload(player));
    }

    public void reset(final @NotNull UUID player) {
        dispatch(listener -> listener.onReset(player));
    }

    public void quit(final @NotNull UUID player) {
        dispatch(listener -> listener.onQuit(player));
    }

    private void dispatch(final @NotNull Consumer<RCPPlayerListener> event) {
        for (final HostedEnvironment environment : environments) {
            for (final RCPPlayerListener listener : environment.listeners()) {
                try {
                    event.accept(listener);
                } catch (final Throwable failure) {
                    environment.logger().warn("A player listener of RCP '{}' failed", environment.metadata().name(), failure);
                }
            }
        }
    }

    public @Nullable RCPCommand command(final @NotNull String name) {
        final String key = name.toLowerCase(Locale.ROOT);
        for (final HostedEnvironment environment : environments) {
            final RCPCommand command = environment.commands().get(key);
            if (command != null) {
                return command;
            }
        }
        return null;
    }

    public @NotNull List<RCPCommand> commands() {
        final List<RCPCommand> commands = new ArrayList<>();
        environments.forEach(environment -> commands.addAll(environment.commands().values()));
        return commands;
    }

    public @NotNull List<? extends HostedEnvironment> environments() {
        return List.copyOf(environments);
    }

    public @Nullable String status() {
        for (final HostedEnvironment environment : environments) {
            final String status = environment.currentStatus();
            if (status != null) {
                return status;
            }
        }
        return null;
    }

    @NotNull Plugin plugin() {
        return plugin;
    }

    @NotNull Scheduler scheduler() {
        return scheduler;
    }

    @NotNull Logger hostLogger() {
        return logger;
    }

}
