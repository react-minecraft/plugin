package com.g4vrk.react.fabric.listener;

import com.g4vrk.react.platform.listener.PlatformListenerScope;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks PacketEvents listeners registered by one extension.
 */
final class FabricExtensionListenerScope implements PlatformListenerScope {

    private final Logger logger;
    private final Set<PacketListenerCommon> packetListeners = new HashSet<>();
    private boolean closed;

    FabricExtensionListenerScope(
            final @NotNull Logger logger
    ) {

        this.logger = logger;

    }

    @Override
    public synchronized void register(
            final @NotNull Object listener
    ) {

        ensureOpen();
        if (listener instanceof final PacketListenerCommon packetListener && packetListeners.add(packetListener)) {
            PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        }

    }

    @Override
    public synchronized void unregister(
            final @NotNull Object listener
    ) {

        if (listener instanceof final PacketListenerCommon packetListener && packetListeners.remove(packetListener)) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }

    }

    @Override
    public synchronized void close() {

        if (closed) {
            return;
        }
        closed = true;
        for (final PacketListenerCommon listener : packetListeners) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            } catch (final RuntimeException failure) {
                logger.warn("Could not unregister an extension packet listener", failure);
            }
        }
        packetListeners.clear();

    }

    private void ensureOpen() {

        if (closed) {
            throw new IllegalStateException("Fabric listener scope is closed");
        }

    }

}
