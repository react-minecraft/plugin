/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.api.channel;

import com.g4vrk.react.api.channel.impl.ReportChannel;
import com.g4vrk.react.api.check.Check;
import com.g4vrk.react.api.event.check.CheckFlagEvent;
import com.g4vrk.react.api.event.check.CheckPunishEvent;
import net.kyori.adventure.audience.Audience;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks subscription filtering, concurrency safety, and check-event validation invariants.
 */
final class ChannelAndRegistryLockTest {

    @Test
    void reportChannelFiltersByTargetAndCheckIdAndResistsConcurrentMutation() throws Exception {

        final ReportChannel channel = new ReportChannel();
        final Audience global = new Audience() { };
        final Audience scoped = new Audience() { };
        final UUID cheater = UUID.randomUUID();
        final UUID bystander = UUID.randomUUID();

        assertTrue(channel.subscribe(global));
        assertTrue(channel.subscribe(scoped, Set.of(cheater), "aim-ai"));
        assertTrue(channel.follows(global, null, null));
        assertFalse(channel.follows(global, Set.of(cheater, bystander), "aim-ai"));
        assertTrue(channel.follows(scoped, Set.of(cheater), "aim-ai"));
        assertFalse(channel.follows(scoped, Set.of(bystander), "aim-ai"));
        assertFalse(channel.follows(scoped, Set.of(cheater), "other-check"));
        assertEquals(Set.of(cheater), channel.targets(scoped));

        assertFalse(channel.toggle(global));
        assertFalse(channel.recipients().contains(global));
        assertTrue(channel.remove(scoped));
        assertTrue(channel.recipients().isEmpty());

        final ExecutorService pool = Executors.newFixedThreadPool(4);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < 4; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    final Audience viewer = new Audience() { };
                    for (int i = 0; i < 150; i++) {
                        channel.toggle(viewer, Set.of(cheater), "aim-ai");
                        channel.follows(viewer, Set.of(cheater), "aim-ai");
                    }
                    channel.remove(viewer);
                    return null;
                }));
            }
            start.countDown();
            for (final Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            assertTrue(channel.recipients().isEmpty());
        } finally {
            pool.shutdownNow();
        }

    }

    @Test
    void checkEventsValidateAmountsAndProtectActionLists() {

        final Check check = (Check) Proxy.newProxyInstance(
                Check.class.getClassLoader(),
                new Class<?>[]{Check.class},
                (proxy, method, args) -> null
        );

        assertThrows(IllegalArgumentException.class, () -> new CheckFlagEvent(check, 0.0D, ""));
        assertThrows(IllegalArgumentException.class, () -> new CheckFlagEvent(check, -1.0D, ""));
        assertThrows(IllegalArgumentException.class, () -> new CheckFlagEvent(check, Double.NaN, ""));
        assertThrows(IllegalArgumentException.class, () -> new CheckFlagEvent(check, Double.POSITIVE_INFINITY, ""));

        final CheckFlagEvent flagEvent = new CheckFlagEvent(check, 1.5D, "yaw=12.3");
        assertThrows(IllegalArgumentException.class, () -> flagEvent.amount(0.0D));
        assertThrows(IllegalArgumentException.class, () -> flagEvent.amount(Double.NaN));
        assertEquals(1.5D, flagEvent.amount(), 1.0E-9D);
        flagEvent.cancelled(true);
        assertTrue(flagEvent.cancelled());

        final List<String> actions = new ArrayList<>(List.of("[alert]", "[console] ban Steve"));
        final CheckPunishEvent punishEvent = new CheckPunishEvent(check, 10.0D, actions);
        actions.clear();
        assertEquals(2, punishEvent.actions().size());
        assertThrows(UnsupportedOperationException.class, () -> punishEvent.actions().add("extra"));

    }

}
