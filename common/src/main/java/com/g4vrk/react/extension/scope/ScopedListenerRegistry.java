package com.g4vrk.react.extension.scope;

import com.g4vrk.react.api.event.Event;
import com.g4vrk.react.api.event.EventDispatcher;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.Subscription;
import com.g4vrk.react.api.extension.ListenerRegistry;
import com.g4vrk.react.event.ScopedEventDispatcher;
import com.g4vrk.react.platform.listener.PlatformListenerScope;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * The listeners of one extension: React events and platform-native listeners, all removed on
 * {@link #close()}.
 */
public final class ScopedListenerRegistry implements ListenerRegistry, AutoCloseable {

    private final ScopedEventDispatcher events;
    private final PlatformListenerScope platformScope;

    public ScopedListenerRegistry(
            final @NotNull EventDispatcher events,
            final @NotNull PlatformListenerScope platformScope
    ) {

        this.events = new ScopedEventDispatcher(events);
        this.platformScope = platformScope;

    }

    @Override
    public synchronized void register(
            final @NotNull Object listener
    ) {

        events.register(listener);
        platformScope.register(listener);

    }

    @Override
    public synchronized void unregister(
            final @NotNull Object listener
    ) {

        platformScope.unregister(listener);
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
    public <E extends Event> @NotNull E post(
            final @NotNull E event
    ) {

        return events.post(event);

    }

    @Override
    public synchronized void close() {

        events.close();
        platformScope.close();

    }

}
