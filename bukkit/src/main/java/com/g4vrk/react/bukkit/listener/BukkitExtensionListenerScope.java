package com.g4vrk.react.bukkit.listener;

import com.g4vrk.react.platform.listener.PlatformListenerScope;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks Bukkit {@link Listener} and PacketEvents {@link PacketListenerCommon} instances owned by
 * one extension and removes them on {@link #close()}.
 */
final class BukkitExtensionListenerScope implements PlatformListenerScope {

    private final Plugin plugin;
    private final Logger logger;
    private final Set<Listener> bukkit = new HashSet<>();
    private final Set<PacketListenerCommon> packets = new HashSet<>();

    BukkitExtensionListenerScope(
            final @NotNull Plugin plugin,
            final @NotNull Logger logger
    ) {

        this.plugin = plugin;
        this.logger = logger;

    }

    @Override
    public synchronized void register(
            final @NotNull Object listener
    ) {

        if (listener instanceof final Listener bukkitListener && bukkit.add(bukkitListener)) {
            Bukkit.getPluginManager().registerEvents(bukkitListener, plugin);
        }
        if (listener instanceof final PacketListenerCommon packetListener && packets.add(packetListener)) {
            PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        }

    }

    @Override
    public synchronized void unregister(
            final @NotNull Object listener
    ) {

        if (listener instanceof final Listener bukkitListener && bukkit.remove(bukkitListener)) {
            HandlerList.unregisterAll(bukkitListener);
        }
        if (listener instanceof final PacketListenerCommon packetListener && packets.remove(packetListener)) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }

    }

    @Override
    public synchronized void close() {

        bukkit.forEach(HandlerList::unregisterAll);
        bukkit.clear();
        for (final PacketListenerCommon listener : packets) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            } catch (final RuntimeException failure) {
                logger.warn("Could not unregister a packet listener", failure);
            }
        }
        packets.clear();

    }

}
