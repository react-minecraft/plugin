/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.util;

import com.g4vrk.react.check.decay.impl.ExponentialDecay;
import com.g4vrk.react.check.decay.impl.LinearDecay;
import com.g4vrk.react.check.decay.impl.NoDecay;
import com.g4vrk.react.registry.MapRegistry;
import com.g4vrk.react.util.math.AngleMath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks angle wrapping, decay math, and MapRegistry concurrency/view invariants.
 */
final class CoreMathAndDecayLockTest {

    @Test
    void angleMathNormalizesWraparoundAndRejectsNonFinite() {

        assertEquals(0.0F, AngleMath.normalizeAngle(360.0F), 1.0E-5F);
        assertEquals(-179.0F, AngleMath.normalizeAngle(181.0F), 1.0E-5F);
        assertEquals(179.0F, AngleMath.normalizeAngle(-181.0F), 1.0E-5F);
        assertEquals(-2.0F, AngleMath.normalizeDelta(358.0F), 1.0E-5F);
        assertEquals(2.0F, AngleMath.normalizeDelta(-358.0F), 1.0E-5F);
        assertEquals(0.0F, AngleMath.normalizeAngle(Float.NaN));
        assertEquals(0.0F, AngleMath.normalizeAngle(Float.POSITIVE_INFINITY));

    }

    @Test
    void decayStrategiesNeverReturnNegativeOrIncreaseViolations() {

        final LinearDecay linear = new LinearDecay(2.0D);
        assertEquals(8.0D, linear.decay(10.0D), 1.0E-9D);
        assertEquals(0.0D, linear.decay(1.0D), 1.0E-9D);

        final ExponentialDecay exponential = new ExponentialDecay(0.5D);
        assertEquals(5.0D, exponential.decay(10.0D), 1.0E-9D);
        assertTrue(exponential.decay(10.0D) >= 0.0D);

        assertEquals(42.0D, NoDecay.INSTANCE.decay(42.0D), 1.0E-9D);

    }

    @Test
    void mapRegistryMaintainsUnmodifiableViewsIdentityKeyLookupAndThreadSafety() throws Exception {

        final MapRegistry<String, String> registry = new MapRegistry<>();
        final String registeredValue = new String("handler");
        registry.register("aim-ai", registeredValue);
        assertEquals(registeredValue, registry.get("aim-ai"));
        assertEquals("aim-ai", registry.getKey(registeredValue));
        assertNull(registry.getKey(new String("handler")));
        registry.register("aim-ai", "updated");
        assertEquals("updated", registry.get("aim-ai"));
        assertThrows(UnsupportedOperationException.class, () -> registry.keySet().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> registry.entrySet().clear());

        final ExecutorService pool = Executors.newFixedThreadPool(4);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < 4; t++) {
                final int worker = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 100; i++) {
                        final String key = "k-" + worker + "-" + i;
                        registry.register(key, key);
                        assertEquals(key, registry.get(key));
                        registry.unregister(key);
                        assertNull(registry.get(key));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (final Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            assertEquals(1, registry.size());
        } finally {
            pool.shutdownNow();
        }

    }

}
