package com.g4vrk.react.sponge.listener;

import com.g4vrk.react.platform.listener.PlatformListenerScope;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.plugin.PluginContainer;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks Sponge event and PacketEvents listeners owned by one extension.
 */
final class SpongeExtensionListenerScope implements PlatformListenerScope {

    private final PluginContainer container;
    private final Logger logger;
    private final Set<Object> spongeListeners = new HashSet<>();
    private final Set<PacketListenerCommon> packetListeners = new HashSet<>();

    SpongeExtensionListenerScope(
            final @NotNull PluginContainer container,
            final @NotNull Logger logger
    ) {

        this.container = container;
        this.logger = logger;

    }

    @Override
    public synchronized void register(final @NotNull Object listener) {

        if (listener instanceof final PacketListenerCommon packetListener) {
            if (packetListeners.add(packetListener)) {
                PacketEvents.getAPI().getEventManager().registerListener(packetListener);
            }
            return;
        }
        if (spongeListeners.add(listener)) {
            SpongeListenerRegistrar.register(container, listener);
        }

    }

    @Override
    public synchronized void unregister(final @NotNull Object listener) {

        if (listener instanceof final PacketListenerCommon packetListener) {
            if (packetListeners.remove(packetListener)) {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            }
            return;
        }
        if (spongeListeners.remove(listener)) {
            Sponge.eventManager().unregisterListeners(listener);
        }

    }

    @Override
    public synchronized void close() {

        for (final Object listener : spongeListeners) {
            try {
                Sponge.eventManager().unregisterListeners(listener);
            } catch (final RuntimeException failure) {
                logger.warn("Could not unregister an extension Sponge listener", failure);
            }
        }
        spongeListeners.clear();
        for (final PacketListenerCommon listener : packetListeners) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            } catch (final RuntimeException failure) {
                logger.warn("Could not unregister an extension packet listener", failure);
            }
        }
        packetListeners.clear();

    }

}
