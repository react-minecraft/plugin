package com.g4vrk.react.event;

import com.g4vrk.react.api.event.CancellableEvent;
import com.g4vrk.react.api.event.Event;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.Subscribe;
import com.g4vrk.react.api.event.Subscription;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleEventBusTest {

    private final SimpleEventBus bus = new SimpleEventBus(NOPLogger.NOP_LOGGER);

    @Test
    void runsHandlersByPriorityThenOrder() {
        final List<String> calls = new ArrayList<>();

        bus.subscribe(TestEvent.class, EventPriority.HIGH, false, event -> calls.add("high"));
        bus.subscribe(TestEvent.class, event -> calls.add("normal-1"));
        bus.subscribe(Event.class, EventPriority.LOWEST, false, event -> calls.add("lowest"));
        bus.subscribe(TestEvent.class, event -> calls.add("normal-2"));

        bus.post(new TestEvent());

        assertEquals(List.of("lowest", "normal-1", "normal-2", "high"), calls);
    }

    @Test
    void skipsCancelledEventsWhenAsked() {
        final List<String> calls = new ArrayList<>();

        bus.subscribe(TestEvent.class, EventPriority.LOW, false, event -> event.cancelled(true));
        bus.subscribe(TestEvent.class, EventPriority.NORMAL, true, event -> calls.add("ignored"));
        bus.subscribe(TestEvent.class, EventPriority.MONITOR, false, event -> calls.add("monitor"));

        assertTrue(bus.post(new TestEvent()).cancelled());
        assertEquals(List.of("monitor"), calls);
    }

    @Test
    void keepsPostingAfterAFailingHandler() {
        final List<String> calls = new ArrayList<>();

        bus.subscribe(TestEvent.class, event -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(TestEvent.class, event -> calls.add("after"));

        bus.post(new TestEvent());

        assertEquals(List.of("after"), calls);
    }

    @Test
    void removesHandlers() {
        final List<String> calls = new ArrayList<>();
        final Listener listener = new Listener(calls);

        bus.register(listener);
        final Subscription subscription = bus.subscribe(TestEvent.class, event -> calls.add("lambda"));
        bus.post(new TestEvent());

        bus.unregister(listener);
        subscription.unsubscribe();
        subscription.unsubscribe();
        bus.post(new TestEvent());

        assertEquals(List.of("listener", "lambda"), calls);
    }

    @Test
    void scopedBusRemovesEverythingOnClose() {
        final List<String> calls = new ArrayList<>();
        final ScopedEventBus scoped = new ScopedEventBus(bus);

        scoped.register(new Listener(calls));
        scoped.subscribe(TestEvent.class, event -> calls.add("lambda"));
        scoped.close();
        bus.post(new TestEvent());

        assertEquals(List.of(), calls);
    }

    @Test
    void rejectsWrongHandlerSignatures() {
        assertThrows(IllegalArgumentException.class, () -> bus.register(new Object() {
            @Subscribe
            void wrong(final String text) {
            }
        }));
    }

    private static final class TestEvent extends CancellableEvent {

    }

    private record Listener(List<String> calls) {

        @Subscribe
        void on(final TestEvent event) {
            calls.add("listener");
        }

    }

}
