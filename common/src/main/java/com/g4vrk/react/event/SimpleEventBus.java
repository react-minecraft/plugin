package com.g4vrk.react.event;

import com.g4vrk.react.api.event.Cancellable;
import com.g4vrk.react.api.event.Event;
import com.g4vrk.react.api.event.EventBus;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.Subscribe;
import com.g4vrk.react.api.event.Subscription;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Event bus that keeps handlers sorted by priority and then by the order they were added.
 *
 * <p>Handler arrays for each posted event class, its supertypes included, are built once and dropped
 * whenever a handler is added or removed, so posting does no sorting and takes no lock.</p>
 */
public final class SimpleEventBus implements EventBus {

    private static final Comparator<Handler> ORDER = Comparator
            .comparingInt((final Handler handler) -> handler.priority().ordinal())
            .thenComparingLong(Handler::order);

    private final Logger logger;

    private final List<Handler> handlers = new ArrayList<>();
    private final Map<Object, List<Subscription>> listeners = new IdentityHashMap<>();
    private final Map<Class<?>, Handler[]> baked = new ConcurrentHashMap<>();
    private final AtomicLong order = new AtomicLong();

    public SimpleEventBus(final @NotNull Logger logger) {
        this.logger = logger;
    }

    @Override
    public void register(final @NotNull Object listener) {
        final List<Subscription> subscriptions = new ArrayList<>();

        for (final Method method : methods(listener.getClass())) {
            final Subscribe subscribe = method.getAnnotation(Subscribe.class);
            final Class<? extends Event> type = eventType(method);
            final MethodHandle handle = handle(method, listener);

            subscriptions.add(subscribe(type, subscribe.priority(), subscribe.ignoreCancelled(), event -> {
                try {
                    handle.invoke(event);
                } catch (final RuntimeException | Error exception) {
                    throw exception;
                } catch (final Throwable throwable) {
                    throw new IllegalStateException(throwable);
                }
            }));
        }

        synchronized (this) {
            listeners.computeIfAbsent(listener, key -> new ArrayList<>()).addAll(subscriptions);
        }
    }

    @Override
    public void unregister(final @NotNull Object listener) {
        final List<Subscription> subscriptions;

        synchronized (this) {
            subscriptions = listeners.remove(listener);
        }

        if (subscriptions != null) {
            subscriptions.forEach(Subscription::unsubscribe);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Event> @NotNull Subscription subscribe(
            final @NotNull Class<E> type,
            final @NotNull EventPriority priority,
            final boolean ignoreCancelled,
            final @NotNull Consumer<? super E> handler
    ) {
        final Handler entry = new Handler(
                type,
                priority,
                ignoreCancelled,
                (Consumer<Event>) handler,
                order.getAndIncrement()
        );

        synchronized (this) {
            handlers.add(entry);
            baked.clear();
        }

        final AtomicBoolean active = new AtomicBoolean(true);

        return () -> {
            if (active.compareAndSet(true, false)) {
                synchronized (this) {
                    handlers.remove(entry);
                    baked.clear();
                }
            }
        };
    }

    @Override
    public <E extends Event> @NotNull E post(final @NotNull E event) {
        Handler[] matching = baked.get(event.getClass());

        if (matching == null) {
            matching = bake(event.getClass());
        }

        for (final Handler handler : matching) {
            if (handler.ignoreCancelled() && event instanceof Cancellable cancellable && cancellable.cancelled()) {
                continue;
            }

            try {
                handler.consumer().accept(event);
            } catch (final Throwable throwable) {
                logger.error("A handler of {} failed", event.getClass().getSimpleName(), throwable);
            }
        }

        return event;
    }

    private synchronized Handler @NotNull [] bake(final @NotNull Class<?> type) {
        final Handler[] matching = handlers.stream()
                .filter(handler -> handler.type().isAssignableFrom(type))
                .sorted(ORDER)
                .toArray(Handler[]::new);

        baked.put(type, matching);
        return matching;
    }

    private static @NotNull List<Method> methods(final @NotNull Class<?> type) {
        final List<Method> methods = new ArrayList<>();

        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Arrays.stream(current.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Subscribe.class))
                    .filter(method -> !method.isBridge() && !method.isSynthetic())
                    .forEach(methods::add);
        }

        return methods;
    }

    @SuppressWarnings("unchecked")
    private static @NotNull Class<? extends Event> eventType(final @NotNull Method method) {
        final Class<?>[] parameters = method.getParameterTypes();

        if (parameters.length != 1 || !Event.class.isAssignableFrom(parameters[0])) {
            throw new IllegalArgumentException(
                    method + " must take exactly one parameter that extends " + Event.class.getName()
            );
        }

        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException(method + " must not be static");
        }

        return (Class<? extends Event>) parameters[0];
    }

    private static @NotNull MethodHandle handle(final @NotNull Method method, final @NotNull Object listener) {
        try {
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method).bindTo(listener);
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalArgumentException("Cannot access " + method, exception);
        }
    }

    private record Handler(
            @NotNull Class<?> type,
            @NotNull EventPriority priority,
            boolean ignoreCancelled,
            @NotNull Consumer<Event> consumer,
            long order
    ) {

    }

}
