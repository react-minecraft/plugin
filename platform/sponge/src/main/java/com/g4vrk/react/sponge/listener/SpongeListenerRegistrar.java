package com.g4vrk.react.sponge.listener;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.EventListenerRegistration;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.Listener;
import org.spongepowered.plugin.PluginContainer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers Sponge listeners with either module lookup or per-method event registration.
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

    static @NotNull List<Object> registerExtension(
            final @NotNull EventManager eventManager,
            final @NotNull PluginContainer container,
            final @NotNull Object listener
    ) {

        return registerExtension(eventManager, container, listener, EventListenerRegistration::builder);

    }

    static @NotNull List<Object> registerExtension(
            final @NotNull EventManager eventManager,
            final @NotNull PluginContainer container,
            final @NotNull Object listener,
            final @NotNull RegistrationFactory builderFactory
    ) {

        final List<Method> methods = collectListenerMethods(listener.getClass());
        if (methods.isEmpty()) {
            return List.of();
        }
        if (!allSingleEventParameter(methods)) {
            try {
                final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        listener.getClass(),
                        MethodHandles.lookup()
                );
                eventManager.registerListeners(container, listener, lookup);
                return List.of(listener);
            } catch (final IllegalAccessException exception) {
                throw new IllegalStateException(
                        "Could not create a lookup for Sponge listener " + listener.getClass().getName(),
                        exception
                );
            }
        }
        final List<Object> handles = new ArrayList<>(methods.size());
        try {
            for (final Method method : methods) {
                final Listener annotation = method.getAnnotation(Listener.class);
                handles.add(registerSingleMethod(eventManager, container, listener, method, annotation, builderFactory));
            }
            return List.copyOf(handles);
        } catch (final RuntimeException | Error failure) {
            for (final Object registered : handles) {
                try {
                    eventManager.unregisterListeners(registered);
                } catch (final RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }

    }

    /**
     * Supplies Sponge event registration builders for a target event type.
     */
    @FunctionalInterface
    interface RegistrationFactory {

        <E extends Event> EventListenerRegistration.@NotNull Builder<E> builder(@NotNull Class<E> eventType);

    }

    private static @NotNull List<Method> collectListenerMethods(final @NotNull Class<?> targetClass) {

        final List<Method> methods = new ArrayList<>();
        for (Class<?> current = targetClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (final Method method : current.getDeclaredMethods()) {
                if (!method.isSynthetic()
                        && !method.isBridge()
                        && !Modifier.isStatic(method.getModifiers())
                        && method.isAnnotationPresent(Listener.class)) {
                    methods.add(method);
                }
            }
        }
        return methods;

    }

    private static boolean allSingleEventParameter(final @NotNull List<Method> methods) {

        for (final Method method : methods) {
            if (method.getParameterCount() != 1) {
                return false;
            }
            final Class<?> parameterType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(parameterType)) {
                return false;
            }
        }
        return true;

    }

    @SuppressWarnings("unchecked")
    private static <E extends Event> @NotNull EventListener<E> registerSingleMethod(
            final @NotNull EventManager eventManager,
            final @NotNull PluginContainer container,
            final @NotNull Object listener,
            final @NotNull Method method,
            final @NotNull Listener annotation,
            final @NotNull RegistrationFactory builderFactory
    ) {

        method.setAccessible(true);
        final MethodHandle bound;
        try {
            bound = MethodHandles.lookup().unreflect(method).bindTo(listener);
        } catch (final IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Could not unreflect Sponge listener method " + method,
                    exception
            );
        }
        final EventListener<E> handler = event -> {
            try {
                bound.invoke(event);
            } catch (final Error | Exception failure) {
                throw failure;
            } catch (final Throwable throwable) {
                throw new IllegalStateException("Sponge extension listener threw an error", throwable);
            }
        };
        final Class<E> eventType = (Class<E>) method.getParameterTypes()[0];
        final EventListenerRegistration<E> registration = builderFactory.builder(eventType)
                .plugin(container)
                .order(annotation.order())
                .beforeModifications(annotation.beforeModifications())
                .listener(handler)
                .build();
        eventManager.registerListener(registration);
        return handler;

    }

}
