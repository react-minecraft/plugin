package com.g4vrk.react.sponge.listener;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.EventListenerRegistration;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.plugin.PluginContainer;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Sponge extension listener registration without requiring cross-module lookup privileges.
 */
final class SpongeExtensionListenerScopeTest {

    @Test
    @SuppressWarnings("unchecked")
    void skipsReactOnlyListenersAndBindsPrivateInnerSpongeListenersCleanly() throws Exception {

        final List<EventListenerRegistration<Event>> registrations = new ArrayList<>();
        final List<Object> unregistered = new ArrayList<>();
        final EventManager manager = (EventManager) Proxy.newProxyInstance(
                EventManager.class.getClassLoader(),
                new Class<?>[] {EventManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "registerListener" -> {
                        registrations.add((EventListenerRegistration<Event>) args[0]);
                        yield proxy;
                    }
                    case "unregisterListeners" -> {
                        unregistered.add(args[0]);
                        yield proxy;
                    }
                    case "registerListeners" -> throw new AssertionError(
                            "Single-parameter extension listeners must use registerListener instead of module lookup"
                    );
                    default -> null;
                }
        );
        final PluginContainer container = (PluginContainer) Proxy.newProxyInstance(
                PluginContainer.class.getClassLoader(),
                new Class<?>[] {PluginContainer.class},
                (proxy, method, args) -> null
        );
        final SpongeListenerRegistrar.RegistrationFactory factory = SpongeExtensionListenerScopeTest::proxyBuilder;

        final SpongeExtensionListenerScope scope = new SpongeExtensionListenerScope(
                container,
                NOPLogger.NOP_LOGGER,
                () -> manager,
                factory
        );
        final ReactOnlyListener reactOnly = new ReactOnlyListener();
        scope.register(reactOnly);
        assertTrue(registrations.isEmpty());

        final PrivateInnerSpongeListener annotated = new PrivateInnerSpongeListener();
        scope.register(annotated);
        assertEquals(1, registrations.size());

        final EventListenerRegistration<Event> registration = registrations.get(0);
        final EventListener<? super Event> registeredListener = registration.listener();
        assertEquals(Order.LATE, registration.order());
        assertTrue(registration.beforeModifications());
        final Event sampleEvent = (Event) Proxy.newProxyInstance(
                Event.class.getClassLoader(),
                new Class<?>[] {Event.class},
                (proxy, method, args) -> null
        );
        registeredListener.handle(sampleEvent);
        assertEquals(1, annotated.calls.get());

        scope.unregister(annotated);
        assertEquals(1, unregistered.size());
        assertEquals(registeredListener, unregistered.get(0));

        scope.register(annotated);
        scope.close();
        assertEquals(2, unregistered.size());

    }

    @SuppressWarnings("unchecked")
    private static <E extends Event> EventListenerRegistration.Builder<E> proxyBuilder(final Class<E> eventType) {

        final AtomicReference<PluginContainer> pluginRef = new AtomicReference<>();
        final AtomicReference<Order> orderRef = new AtomicReference<>(Order.DEFAULT);
        final AtomicBoolean beforeModRef = new AtomicBoolean();
        final AtomicReference<EventListener<? super E>> listenerRef = new AtomicReference<>();

        return (EventListenerRegistration.Builder<E>) Proxy.newProxyInstance(
                EventListenerRegistration.class.getClassLoader(),
                new Class<?>[] {EventListenerRegistration.Builder.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "plugin" -> {
                        pluginRef.set((PluginContainer) args[0]);
                        yield proxy;
                    }
                    case "order" -> {
                        orderRef.set((Order) args[0]);
                        yield proxy;
                    }
                    case "beforeModifications" -> {
                        beforeModRef.set((Boolean) args[0]);
                        yield proxy;
                    }
                    case "listener" -> {
                        listenerRef.set((EventListener<? super E>) args[0]);
                        yield proxy;
                    }
                    case "build" -> Proxy.newProxyInstance(
                            EventListenerRegistration.class.getClassLoader(),
                            new Class<?>[] {EventListenerRegistration.class},
                            (regProxy, regMethod, regArgs) -> switch (regMethod.getName()) {
                                case "eventType" -> eventType;
                                case "plugin" -> pluginRef.get();
                                case "order" -> orderRef.get();
                                case "beforeModifications" -> beforeModRef.get();
                                case "listener" -> listenerRef.get();
                                default -> null;
                            }
                    );
                    default -> proxy;
                }
        );

    }

    /**
     * Simulates a listener with only React annotations and no Sponge {@link Listener} methods.
     */
    private static final class ReactOnlyListener {

        @SuppressWarnings("unused")
        public void onReactEvent() {

        }

    }

    /**
     * Simulates a private extension listener class using Sponge's {@link Listener} annotation.
     */
    private static final class PrivateInnerSpongeListener {

        private final AtomicInteger calls = new AtomicInteger();

        @Listener(order = Order.LATE, beforeModifications = true)
        private void onEvent(final Event event) {

            if (event != null) {
                calls.incrementAndGet();
            }

        }

    }

}
