package com.g4vrk.react.bukkit.command;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Refreshes supported Bukkit command dispatchers when extensions change the Cloud tree.
 */
final class BukkitCommandTreeRefresh implements Consumer<String> {

    private final Plugin plugin;
    private final CommandManager<PlatformSender> manager;
    private final TaskScheduler<PlatformPlayer> scheduler;
    private final @Nullable Method syncCommands;
    private final boolean mirroredDispatcher;
    private final Set<String> changedRoots = new HashSet<>();
    private boolean queued;

    BukkitCommandTreeRefresh(
            final @NotNull Plugin plugin,
            final @NotNull CommandManager<PlatformSender> manager,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    ) {

        this.plugin = plugin;
        this.manager = manager;
        this.scheduler = scheduler;
        this.syncCommands = legacySync(plugin.getServer());
        this.mirroredDispatcher = hasClass(plugin.getServer(), "io.papermc.paper.command.brigadier.ApiMirrorRootNode");

    }

    @Override
    public void accept(
            final @NotNull String label
    ) {

        synchronized (manager) {
            if ((syncCommands == null && !mirroredDispatcher) || !plugin.isEnabled()) {
                return;
            }
            if (mirroredDispatcher) {
                changedRoots.add(label);
            }
            if (queued) {
                return;
            }
            queued = true;
            try {
                scheduler.schedule(this::refresh, TickSchedule.instant());
            } catch (final IllegalPluginAccessException stopped) {
                queued = false;
                if (plugin.isEnabled()) {
                    throw stopped;
                }
            }
        }

    }

    private void refresh() {

        synchronized (manager) {
            queued = false;
            if (!plugin.isEnabled()) {
                changedRoots.clear();
                return;
            }
            if (mirroredDispatcher) {
                refreshMirroredDispatcher();
                return;
            }
            final Method sync = syncCommands;
            if (sync == null) {
                return;
            }
            try {
                final Server server = plugin.getServer();
                sync.invoke(server);
                for (final Player player : server.getOnlinePlayers()) {
                    final Method updateCommands = findMethod(player.getClass(), "updateCommands");
                    if (updateCommands != null) {
                        updateCommands.invoke(player);
                    }
                }
            } catch (final ReflectiveOperationException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not refresh the legacy Brigadier tree", failure);
            }
        }

    }

    private void refreshMirroredDispatcher() {

        final CommandRegistrationHandler<PlatformSender> registration = manager.commandRegistrationHandler();
        for (final Command<PlatformSender> command : manager.commands()) {
            final String label = command.rootComponent().name();
            if (changedRoots.remove(label)) {
                registration.unregisterRootCommand(command.rootComponent());
                if (!registration.registerCommand(command)) {
                    plugin.getLogger().warning("Could not refresh the Brigadier tree for /" + label);
                }
            }
        }
        changedRoots.clear();

    }

    private static @Nullable Method legacySync(
            final @NotNull Server server
    ) {

        if (hasClass(server, "io.papermc.paper.command.brigadier.CommandSourceStack")) {
            return null;
        }
        try {
            return server.getClass().getMethod("syncCommands");
        } catch (final NoSuchMethodException unavailable) {
            return null;
        }

    }

    private static @Nullable Method findMethod(
            final @NotNull Class<?> type,
            final @NotNull String name
    ) {

        try {
            return type.getMethod(name);
        } catch (final NoSuchMethodException ignored) {
            return null;
        }

    }

    private static boolean hasClass(
            final @NotNull Server server,
            final @NotNull String name
    ) {

        try {
            Class.forName(name, false, server.getClass().getClassLoader());
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }

    }

}
