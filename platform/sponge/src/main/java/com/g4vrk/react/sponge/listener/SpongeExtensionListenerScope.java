package com.g4vrk.react.sponge.listener;

import com.g4vrk.react.platform.listener.PlatformListenerScope;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.EventListenerRegistration;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.plugin.PluginContainer;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Tracks Sponge event and PacketEvents listeners owned by one extension.
 */
final class SpongeExtensionListenerScope implements PlatformListenerScope {

    private final PluginContainer container;
    private final Logger logger;
    private final Supplier<EventManager> eventManager;
    private final SpongeListenerRegistrar.RegistrationFactory registrationFactory;
    private final Map<Object, List<Object>> spongeListeners = new IdentityHashMap<>();
    private final Set<PacketListenerCommon> packetListeners = new HashSet<>();

    SpongeExtensionListenerScope(
            final @NotNull PluginContainer container,
            final @NotNull Logger logger
    ) {

        this(container, logger, Sponge::eventManager, EventListenerRegistration::builder);

    }

    SpongeExtensionListenerScope(
            final @NotNull PluginContainer container,
            final @NotNull Logger logger,
            final @NotNull Supplier<EventManager> eventManager,
            final @NotNull SpongeListenerRegistrar.RegistrationFactory registrationFactory
    ) {

        this.container = container;
        this.logger = logger;
        this.eventManager = eventManager;
        this.registrationFactory = registrationFactory;

    }

    @Override
    public synchronized void register(final @NotNull Object listener) {

        if (listener instanceof final PacketListenerCommon packetListener) {
            if (packetListeners.add(packetListener)) {
                PacketEvents.getAPI().getEventManager().registerListener(packetListener);
            }
            return;
        }
        if (!spongeListeners.containsKey(listener)) {
            final List<Object> handles = SpongeListenerRegistrar.registerExtension(
                    eventManager.get(),
                    container,
                    listener,
                    registrationFactory
            );
            if (!handles.isEmpty()) {
                spongeListeners.put(listener, handles);
            }
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
        final List<Object> handles = spongeListeners.remove(listener);
        if (handles != null) {
            final EventManager manager = eventManager.get();
            for (final Object handle : handles) {
                manager.unregisterListeners(handle);
            }
        }

    }

    @Override
    public synchronized void close() {

        if (!spongeListeners.isEmpty()) {
            final EventManager manager = eventManager.get();
            for (final List<Object> handles : spongeListeners.values()) {
                for (final Object handle : handles) {
                    try {
                        manager.unregisterListeners(handle);
                    } catch (final RuntimeException failure) {
                        logger.warn("Could not unregister an extension Sponge listener", failure);
                    }
                }
            }
            spongeListeners.clear();
        }
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
