package com.g4vrk.react.storage.buffer;

import com.g4vrk.react.storage.model.PlayerStorageData;
import com.g4vrk.react.storage.model.StorageMutation;
import com.g4vrk.react.storage.model.StoredViolation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded FIFO queue holding buffered storage writes and merging un-flushed mutations onto loaded player data.
 */
public final class PendingWriteBuffer {

    private final Deque<StorageMutation> queue = new ArrayDeque<>();
    private final Object lock = new Object();
    private final int capacity;

    public PendingWriteBuffer(
            final int capacity
    ) {

        if (capacity <= 0) {
            throw new IllegalArgumentException("Pending write buffer capacity must be positive");
        }
        this.capacity = capacity;

    }

    public @NotNull Object lock() {

        return lock;

    }

    /**
     * Appends a mutation, evicting the oldest queued entry if full.
     *
     * @return {@code true} if the oldest entry was discarded to make room
     */
    public boolean enqueue(
            final @NotNull StorageMutation mutation
    ) {

        synchronized (lock) {
            final boolean discarded = queue.size() == capacity;
            if (discarded) {
                queue.removeFirst();
            }
            queue.addLast(mutation);
            return discarded;
        }

    }

    public boolean hasPending() {

        synchronized (lock) {
            return !queue.isEmpty();
        }

    }

    public @NotNull List<StorageMutation> drainBatch(
            final int batchSize
    ) {

        final List<StorageMutation> batch = new ArrayList<>(batchSize);
        synchronized (lock) {
            while (batch.size() < batchSize) {
                final StorageMutation mutation = queue.pollFirst();
                if (mutation == null) {
                    break;
                }
                batch.add(mutation);
            }
        }
        return batch;

    }

    /**
     * Pushes an unacknowledged batch back to the front of the queue without displacing newer queued writes.
     *
     * @return {@code true} if any entry from the batch could not fit back and was dropped
     */
    public boolean requeue(
            final @NotNull List<StorageMutation> batch
    ) {

        synchronized (lock) {
            final int availableSlots = Math.max(0, capacity - queue.size());
            final int firstRetained = Math.max(0, batch.size() - availableSlots);
            for (int index = batch.size() - 1; index >= firstRetained; index--) {
                queue.addFirst(batch.get(index));
            }
            return firstRetained > 0;
        }

    }

    /**
     * Overlays all un-flushed mutations for {@code playerUuid} on top of storage data read under {@link #lock()}.
     */
    public @NotNull PlayerStorageData overlay(
            final @NotNull UUID playerUuid,
            final @NotNull PlayerStorageData stored
    ) {

        Map<String, StoredViolation> violations = null;
        Map<String, Long> bypasses = null;
        synchronized (lock) {
            for (final StorageMutation mutation : queue) {
                if (!mutation.playerUuid().equals(playerUuid)) {
                    continue;
                }
                if (mutation instanceof final StorageMutation.Violation violation) {
                    if (violations == null) {
                        violations = new HashMap<>(stored.violations());
                    }
                    putViolation(violations, violation);
                } else if (mutation instanceof final StorageMutation.PendingViolation pending) {
                    if (violations == null) {
                        violations = new HashMap<>(stored.violations());
                    }
                    final StorageMutation.Violation violation = pending.materialize(violations.get(pending.check()));
                    putViolation(violations, violation);
                } else if (mutation instanceof final StorageMutation.BypassMutation bypass) {
                    if (bypasses == null) {
                        bypasses = new HashMap<>(stored.bypasses());
                    }
                    if (bypass.lifted()) {
                        bypasses.remove(bypass.check());
                    } else {
                        bypasses.put(bypass.check(), bypass.until());
                    }
                }
            }
        }
        if (violations == null && bypasses == null) {
            return stored;
        }
        return new PlayerStorageData(
                violations == null ? stored.violations() : violations,
                stored.inferenceHistory(),
                bypasses == null ? stored.bypasses() : bypasses
        );

    }

    public static void putViolation(
            final @NotNull Map<String, StoredViolation> levels,
            final @NotNull StorageMutation.Violation violation
    ) {

        final StoredViolation previous = levels.get(violation.check());
        if (previous == null || violation.updatedAt() >= previous.updatedAt()) {
            levels.put(violation.check(), new StoredViolation(violation.violations(), violation.updatedAt()));
        }

    }

}
