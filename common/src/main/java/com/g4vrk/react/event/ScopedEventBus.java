package com.g4vrk.react.event;

import com.g4vrk.react.api.event.Event;
import com.g4vrk.react.api.event.EventBus;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.Subscription;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * View of an {@link EventBus} for one addon or RCP that remembers what it added.
 *
 * <p>{@link #close()} removes all of it, so a rebooted extension leaves no handlers behind.</p>
 */
public final class ScopedEventBus implements EventBus, AutoCloseable {

    private final EventBus delegate;

    private final Set<Object> listeners = ConcurrentHashMap.newKeySet();
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    public ScopedEventBus(final @NotNull EventBus delegate) {
        this.delegate = delegate;
    }

    @Override
    public void register(final @NotNull Object listener) {
        delegate.register(listener);
        listeners.add(listener);
    }

    @Override
    public void unregister(final @NotNull Object listener) {
        listeners.remove(listener);
        delegate.unregister(listener);
    }

    @Override
    public <E extends Event> @NotNull Subscription subscribe(
            final @NotNull Class<E> type,
            final @NotNull EventPriority priority,
            final boolean ignoreCancelled,
            final @NotNull Consumer<? super E> handler
    ) {
        final Subscription subscription = delegate.subscribe(type, priority, ignoreCancelled, handler);
        final Subscription tracked = new Subscription() {
            @Override
            public void unsubscribe() {
                subscriptions.remove(this);
                subscription.unsubscribe();
            }
        };

        subscriptions.add(tracked);
        return tracked;
    }

    @Override
    public <E extends Event> @NotNull E post(final @NotNull E event) {
        return delegate.post(event);
    }

    @Override
    public void close() {
        listeners.forEach(delegate::unregister);
        listeners.clear();
        subscriptions.forEach(Subscription::unsubscribe);
    }

}
