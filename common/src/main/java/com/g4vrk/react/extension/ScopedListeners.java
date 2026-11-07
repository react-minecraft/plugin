package com.g4vrk.react.extension;

import com.g4vrk.react.api.event.Event;
import com.g4vrk.react.api.event.EventBus;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.Subscription;
import com.g4vrk.react.api.extension.ListenerRegistry;
import com.g4vrk.react.event.ScopedEventBus;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The listeners of one extension: React events, Bukkit listeners and PacketEvents listeners,
 * all removed on {@link #close()}.
 */
public final class ScopedListeners implements ListenerRegistry, AutoCloseable {

    private final Plugin plugin;
    private final Logger logger;
    private final ScopedEventBus events;

    private final Set<Listener> bukkit = ConcurrentHashMap.newKeySet();
    private final Set<PacketListenerCommon> packets = ConcurrentHashMap.newKeySet();

    public ScopedListeners(final @NotNull Plugin plugin, final @NotNull EventBus events, final @NotNull Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.events = new ScopedEventBus(events);
    }

    @Override
    public void register(final @NotNull Object listener) {
        if (listener instanceof Listener bukkitListener && bukkit.add(bukkitListener)) {
            Bukkit.getPluginManager().registerEvents(bukkitListener, plugin);
        }
        if (listener instanceof PacketListenerCommon packetListener && packets.add(packetListener)) {
            PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        }
        events.register(listener);
    }

    @Override
    public void unregister(final @NotNull Object listener) {
        if (listener instanceof Listener bukkitListener && bukkit.remove(bukkitListener)) {
            HandlerList.unregisterAll(bukkitListener);
        }
        if (listener instanceof PacketListenerCommon packetListener && packets.remove(packetListener)) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }
        events.unregister(listener);
    }

    @Override
    public <E extends Event> @NotNull Subscription subscribe(
            final @NotNull Class<E> type,
            final @NotNull EventPriority priority,
            final boolean ignoreCancelled,
            final @NotNull Consumer<? super E> handler
    ) {
        return events.subscribe(type, priority, ignoreCancelled, handler);
    }

    @Override
    public <E extends Event> @NotNull E post(final @NotNull E event) {
        return events.post(event);
    }

    @Override
    public void close() {
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
        events.close();
    }

}
