package com.g4vrk.react.api.event;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Posts plugin events to handlers, ordered by {@link EventPriority}.
 *
 * <p>Thread-safe: handlers can be added and removed while events are posted. A handler that
 * throws is logged and the remaining handlers still run.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface EventBus {

    /**
     * Registers every {@link Subscribe} method of an object.
     *
     * @param listener the object with handler methods
     * @throws IllegalArgumentException when a handler method has a wrong signature
     */
    void register(final @NotNull Object listener);

    /**
     * Removes every handler that {@link #register(Object)} added for an object.
     *
     * @param listener the object given to {@link #register(Object)}
     */
    void unregister(final @NotNull Object listener);

    /**
     * Adds a handler at {@link EventPriority#NORMAL}.
     *
     * @param type    the event type, subtypes are delivered too
     * @param handler the code to run
     * @param <E>     the event type
     * @return the subscription to remove the handler with
     */
    default <E extends Event> @NotNull Subscription subscribe(
            final @NotNull Class<E> type,
            final @NotNull Consumer<? super E> handler
    ) {
        return subscribe(type, EventPriority.NORMAL, false, handler);
    }

    /**
     * Adds a handler.
     *
     * @param type            the event type, subtypes are delivered too
     * @param priority        when the handler runs
     * @param ignoreCancelled {@code true} to skip cancelled events
     * @param handler         the code to run
     * @param <E>             the event type
     * @return the subscription to remove the handler with
     */
    <E extends Event> @NotNull Subscription subscribe(
            final @NotNull Class<E> type,
            final @NotNull EventPriority priority,
            final boolean ignoreCancelled,
            final @NotNull Consumer<? super E> handler
    );

    /**
     * Posts an event to every matching handler on the calling thread.
     *
     * @param event the event
     * @param <E>   the event type
     * @return the same event, to read the cancel state and changes from
     */
    <E extends Event> @NotNull E post(final @NotNull E event);

}
