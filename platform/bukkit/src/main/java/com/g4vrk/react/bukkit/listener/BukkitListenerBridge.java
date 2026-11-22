package com.g4vrk.react.bukkit.listener;

import com.g4vrk.react.platform.listener.CoreListenerCallback;
import com.g4vrk.react.platform.listener.PlatformListenerScope;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Bukkit and PacketEvents implementation of {@link PlatformListenerBridge}.
 */
public final class BukkitListenerBridge implements PlatformListenerBridge {

    private final Plugin plugin;
    private final Logger logger;

    private PacketListenerCommon[] packetListeners = new PacketListenerCommon[0];
    private Listener[] bukkitListeners = new Listener[0];

    public BukkitListenerBridge(
            final @NotNull Plugin plugin,
            final @NotNull Logger logger
    ) {

        this.plugin = plugin;
        this.logger = logger;

    }

    @Override
    public void registerCore(
            final @NotNull CoreListenerCallback callbacks
    ) {

        logger.info("Registering packet listeners...");
        this.packetListeners = new PacketListenerCommon[]{
                new PacketEventsRotationListener(callbacks)
        };
        PacketEvents.getAPI().getEventManager().registerListeners(packetListeners);

        logger.info("Registering bukkit listeners...");
        final PluginManager pluginManager = plugin.getServer().getPluginManager();
        final Listener combat = new BukkitCombatListener(callbacks);
        final Listener connection = new BukkitConnectionListener(callbacks);
        this.bukkitListeners = new Listener[]{combat, connection};
        for (final Listener listener : bukkitListeners) {
            pluginManager.registerEvents(listener, plugin);
        }

    }

    @Override
    public void unregisterCore() {

        try {
            if (packetListeners.length > 0) {
                logger.info("Unregistering listeners...");
                PacketEvents.getAPI().getEventManager().unregisterListeners(packetListeners);
            }
        } finally {
            packetListeners = new PacketListenerCommon[0];
        }

        for (final Listener listener : bukkitListeners) {
            HandlerList.unregisterAll(listener);
        }
        bukkitListeners = new Listener[0];

    }

    @Override
    public @NotNull PlatformListenerScope createExtensionScope(
            final @NotNull Logger extensionLogger
    ) {

        return new BukkitExtensionListenerScope(plugin, extensionLogger);

    }

}
