package com.g4vrk.react.command;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Rebuilds Paper's legacy dispatcher when extensions change the Cloud tree after startup.
 */
public final class LegacyCommandTreeRefresh implements Runnable {

    private final Plugin plugin;
    private final CommandManager<CommandSender> manager;
    private final @Nullable Method syncCommands;
    private final AtomicBoolean queued = new AtomicBoolean();

    public LegacyCommandTreeRefresh(
            final @NotNull Plugin plugin,
            final @NotNull CommandManager<CommandSender> manager
    ) {

        this.plugin = plugin;
        this.manager = manager;
        this.syncCommands = legacySync(plugin.getServer());

    }

    @Override
    public void run() {

        if (syncCommands != null && plugin.isEnabled() && queued.compareAndSet(false, true)) {
            try {
                plugin.getServer().getScheduler().runTask(plugin, this::refresh);
            } catch (final IllegalPluginAccessException stopped) {
                queued.set(false);
                if (plugin.isEnabled()) {
                    throw stopped;
                }
            }
        }

    }

    private void refresh() {

        synchronized (manager) {
            queued.set(false);
            final Method sync = syncCommands;
            if (sync == null) {
                return;
            }
            try {
                final Server server = plugin.getServer();
                sync.invoke(server);
                for (final Player player : server.getOnlinePlayers()) {
                    player.updateCommands();
                }
            } catch (final ReflectiveOperationException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not refresh the legacy Brigadier tree", failure);
            }
        }

    }

    private static @Nullable Method legacySync(final @NotNull Server server) {

        try {
            Class.forName("io.papermc.paper.command.brigadier.CommandSourceStack", false, server.getClass().getClassLoader());
            return null;
        } catch (final ClassNotFoundException ignored) {
            try {
                return server.getClass().getMethod("syncCommands");
            } catch (final NoSuchMethodException unavailable) {
                return null;
            }
        }

    }

}
