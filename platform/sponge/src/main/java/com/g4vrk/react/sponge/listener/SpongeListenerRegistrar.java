package com.g4vrk.react.sponge.listener;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.Sponge;
import org.spongepowered.plugin.PluginContainer;

import java.lang.invoke.MethodHandles;

/**
 * Registers Sponge listeners with a lookup that can access their declaring class.
 */
final class SpongeListenerRegistrar {

    private SpongeListenerRegistrar() {

    }

    static void register(
            final @NotNull PluginContainer container,
            final @NotNull Object listener
    ) {

        try {
            final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    listener.getClass(),
                    MethodHandles.lookup()
            );
            Sponge.eventManager().registerListeners(container, listener, lookup);
        } catch (final IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Could not create a lookup for Sponge listener " + listener.getClass().getName(),
                    exception
            );
        }

    }

}
