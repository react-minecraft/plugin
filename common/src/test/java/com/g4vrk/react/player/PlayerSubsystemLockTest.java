/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.player;

import com.g4vrk.react.api.rcp.player.RotationSample;
import com.g4vrk.react.player.rotation.RotationData;
import com.g4vrk.react.util.math.AngleMath;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies rotation ring-buffer math, wrap-around deltas, thread safety, and combat window invariants.
 */
final class PlayerSubsystemLockTest {

    @Test
    void rotationDataNormalizesWrapAroundDeltasAndMaintainsRingOrder() {

        assertThrows(IllegalArgumentException.class, () -> new RotationData(0));
        final RotationData rotations = new RotationData(3);

        assertNull(rotations.current());
        assertNull(rotations.previous());
        assertEquals(0, rotations.historySize());
        assertEquals(3, rotations.historyCapacity());

        rotations.push(179.0F, 10.0F);
        assertNotNull(rotations.current());
        assertNull(rotations.previous());
        assertEquals(0.0F, rotations.current()[RotationSample.DELTA_YAW], 1.0E-4F);

        rotations.push(-179.0F, 12.0F);
        assertEquals(2.0F, rotations.current()[RotationSample.DELTA_YAW], 1.0E-4F);
        assertEquals(2.0F, rotations.current()[RotationSample.DELTA_PITCH], 1.0E-4F);
        assertNotNull(rotations.previous());
        assertEquals(179.0F, rotations.previous()[RotationSample.YAW], 1.0E-4F);

        rotations.push(-175.0F, 16.0F);
        rotations.push(-170.0F, 20.0F);

        final float[] history = rotations.history();
        assertEquals(3, RotationSample.count(history));
        assertEquals(-179.0F, RotationSample.yaw(history, 0), 1.0E-4F);
        assertEquals(-175.0F, RotationSample.yaw(history, 1), 1.0E-4F);
        assertEquals(-170.0F, RotationSample.yaw(history, 2), 1.0E-4F);

        rotations.clear();
        assertNull(rotations.current());
        assertNull(rotations.previous());
        assertEquals(0, rotations.historySize());
        assertEquals(0, rotations.history().length);

    }

    @Test
    void rotationDataSurvivesConcurrentPushAndHistoryReads() throws Exception {

        final RotationData rotations = new RotationData(16);
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            final Future<?> writer = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 5_000; i++) {
                    rotations.push(i % 360, (i % 180) - 90);
                }
                return null;
            });
            final Future<?> reader = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 5_000; i++) {
                    final float[] snapshot = rotations.history();
                    assertEquals(0, snapshot.length % RotationSample.STRIDE);
                    assertTrue(RotationSample.count(snapshot) <= rotations.historyCapacity());
                }
                return null;
            });
            start.countDown();
            writer.get();
            reader.get();
            assertEquals(16, rotations.historySize());
        } finally {
            pool.shutdownNow();
        }

    }

    @Test
    void combatActivityNeverShrinksAndClampsOverflow() {

        final CombatActivity combat = new CombatActivity();
        assertFalse(combat.active());

        combat.extend(20L);
        assertTrue(combat.active());
        final long firstEnd = combat.activityUntil();

        combat.extend(1L);
        assertTrue(combat.activityUntil() >= firstEnd);

        combat.extend(Long.MAX_VALUE);
        assertTrue(combat.active());
        assertEquals(Long.MAX_VALUE, combat.activityUntil());

        combat.reset();
        assertFalse(combat.active());
        assertEquals(0L, combat.activityUntil());

    }

    @Test
    void angleMathHandlesNonFiniteAndBoundaryAngles() {

        assertEquals(0.0F, AngleMath.normalizeAngle(Float.NaN));
        assertEquals(0.0F, AngleMath.normalizeDelta(Float.POSITIVE_INFINITY));
        assertEquals(-170.0F, AngleMath.normalizeAngle(190.0F), 1.0E-4F);
        assertEquals(170.0F, AngleMath.normalizeAngle(-190.0F), 1.0E-4F);
        assertEquals(0.0F, AngleMath.calculateGCDError(0.0F));
        assertEquals(3.0F, AngleMath.calculateAcceleration(5.0F, 2.0F), 1.0E-4F);
        assertEquals(1.0F, AngleMath.calculateJerk(3.0F, 2.0F), 1.0E-4F);

    }

}
