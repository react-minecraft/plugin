package com.g4vrk.react.sponge.listener;

import com.g4vrk.react.platform.listener.CoreListenerCallback;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.listener.PlatformListenerScope;
import com.g4vrk.react.sponge.listener.packet.SpongeRotationListener;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.plugin.PluginContainer;

/**
 * Sponge and PacketEvents implementation of React's listener bridge.
 */
public final class SpongeListenerBridge implements PlatformListenerBridge {

    private final PluginContainer container;
    private final Logger logger;

    private PacketListenerCommon[] packetListeners = new PacketListenerCommon[0];
    private Object[] spongeListeners = new Object[0];

    public SpongeListenerBridge(
            final @NotNull PluginContainer container,
            final @NotNull Logger logger
    ) {

        this.container = container;
        this.logger = logger;

    }

    @Override
    public synchronized void registerCore(final @NotNull CoreListenerCallback callbacks) {

        unregisterCore();
        final PacketListenerCommon rotation = new SpongeRotationListener(callbacks);
        final Object connection = new SpongeConnectionListener(callbacks);
        final Object combat = new SpongeCombatListener(callbacks);
        try {
            PacketEvents.getAPI().getEventManager().registerListener(rotation);
            Sponge.eventManager().registerListeners(container, connection);
            Sponge.eventManager().registerListeners(container, combat);
            this.packetListeners = new PacketListenerCommon[]{rotation};
            this.spongeListeners = new Object[]{connection, combat};
        } catch (final RuntimeException | Error failure) {
            try {
                Sponge.eventManager().unregisterListeners(connection);
                Sponge.eventManager().unregisterListeners(combat);
            } catch (final RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(rotation);
            } catch (final RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }

    }

    @Override
    public synchronized void unregisterCore() {

        for (final Object listener : spongeListeners) {
            try {
                Sponge.eventManager().unregisterListeners(listener);
            } catch (final RuntimeException failure) {
                logger.warn("Could not unregister a Sponge listener", failure);
            }
        }
        spongeListeners = new Object[0];
        try {
            if (packetListeners.length > 0 && PacketEvents.getAPI() != null) {
                PacketEvents.getAPI().getEventManager().unregisterListeners(packetListeners);
            }
        } catch (final RuntimeException failure) {
            logger.warn("Could not unregister PacketEvents listeners", failure);
        } finally {
            packetListeners = new PacketListenerCommon[0];
        }

    }

    @Override
    public @NotNull PlatformListenerScope createExtensionScope(final @NotNull Logger extensionLogger) {

        return new SpongeExtensionListenerScope(container, extensionLogger);

    }

}
