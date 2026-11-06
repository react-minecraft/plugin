package com.g4vrk.react.rcp;

import com.g4vrk.react.api.rcp.command.RCPCommand;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.event.RCPPlayerListener;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.RCPPlayers;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HostedEnvironment implements RCPEnvironment {

    private static final Set<String> RESERVED_COMMANDS = Set.of(
            "alerts", "verbose", "reload", "history", "reboot", "rcps", "help"
    );

    private final RCPHost host;
    private final RCPMetadata metadata;
    private final Path directory;
    private final Logger logger;

    private final List<RCPPlayerListener> listeners = new CopyOnWriteArrayList<>();
    private final List<PacketListenerCommon> packetListeners = new CopyOnWriteArrayList<>();
    private final Map<String, RCPCommand> commands = new ConcurrentHashMap<>();

    private volatile @Nullable String status;
    private volatile boolean released;

    HostedEnvironment(
            final @NotNull RCPHost host,
            final @NotNull RCPMetadata metadata,
            final @NotNull Path directory,
            final @NotNull Logger logger
    ) {
        this.host = host;
        this.metadata = metadata;
        this.directory = directory;
        this.logger = logger;
    }

    @Override
    public @NotNull Path directory() {
        return directory;
    }

    @Override
    public @NotNull RCPMetadata metadata() {
        return metadata;
    }

    @Override
    public @NotNull Logger logger() {
        return logger;
    }

    @Override
    public @NotNull Plugin plugin() {
        return host.plugin();
    }

    @Override
    public @NotNull Scheduler scheduler() {
        return host.scheduler();
    }

    @Override
    public @NotNull RCPPlayers players() {
        return host;
    }

    @Override
    public void fire(final @NotNull RCPPlayer player, final @NotNull InferenceResult result) {
        Objects.requireNonNull(result, "result");
        if (released || !(player instanceof ReactPlayer reactPlayer)) {
            return;
        }
        host.scheduler().scheduleEntity(reactPlayer.bukkitPlayer, () -> {
            final AimAI check = reactPlayer.checkManager.getCheck(AimAI.class);
            if (check != null) {
                check.onResult(result);
            }
        }, TickSchedule.instant());
    }

    @Override
    public void listen(final @NotNull RCPPlayerListener listener) {
        ensureActive();
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void registerPacketListener(final @NotNull Object listener) {
        ensureActive();
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            throw new IllegalStateException("PacketEvents is not installed");
        }
        if (!(listener instanceof PacketListenerCommon packetListener)) {
            throw new IllegalArgumentException("Not a PacketEvents listener: " + listener.getClass().getName());
        }
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        packetListeners.add(packetListener);
    }

    @Override
    public void registerCommand(final @NotNull RCPCommand command) {
        ensureActive();
        final String name = command.name().toLowerCase(Locale.ROOT);
        if (RESERVED_COMMANDS.contains(name)) {
            throw new IllegalArgumentException("/react " + name + " belongs to the plugin");
        }
        final RCPCommand taken = host.command(name);
        if (taken != null || commands.putIfAbsent(name, command) != null) {
            throw new IllegalArgumentException("/react " + name + " is already registered");
        }
    }

    @Override
    public void status(final @Nullable String status) {
        this.status = status;
    }

    public @Nullable String currentStatus() {
        return released ? null : status;
    }

    @NotNull List<RCPPlayerListener> listeners() {
        return listeners;
    }

    @NotNull Map<String, RCPCommand> commands() {
        return commands;
    }

    private void ensureActive() {
        if (released) {
            throw new IllegalStateException("RCP '" + metadata.name() + "' is disabled");
        }
    }

    void release() {
        released = true;
        listeners.clear();
        commands.clear();
        status = null;
        for (final PacketListenerCommon listener : packetListeners) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            } catch (final Throwable failure) {
                host.hostLogger().warn("Could not unregister a packet listener of RCP '{}'", metadata.name(), failure);
            }
        }
        packetListeners.clear();
    }

}
