package com.g4vrk.react.storage;

import com.g4vrk.react.check.decay.DecayStrategy;
import com.g4vrk.react.storage.model.StorageMutation;
import com.g4vrk.react.storage.model.StoredViolation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A session's violation level, serialized with the storage queue until its base is loaded.
 */
public final class ViolationLevel {

    private final StorageManager storage;
    private final UUID playerUuid;
    private final String check;

    private volatile double value;
    private boolean loaded;
    private boolean attached = true;
    private long updatedAt;

    public ViolationLevel(
            final @NotNull StorageManager storage,
            final @NotNull UUID playerUuid,
            final @NotNull String check
    ) {

        this.storage = storage;
        this.playerUuid = playerUuid;
        this.check = check;
        this.loaded = !storage.enabled();

    }

    public double value() {

        return value;

    }

    public double add(final double amount) {

        synchronized (storage.pendingWriteLock()) {
            final double previous = value;
            final double next = previous + amount;
            if (!Double.isFinite(next)) {
                throw new IllegalArgumentException("Violation level must remain finite");
            }
            value = next;
            if (attached && storage.enabled()) {
                if (loaded) {
                    save(false);
                } else {
                    storage.enqueue(new StorageMutation.PendingViolation(playerUuid, check, base -> base + amount, now()), false);
                }
            }
            return previous;
        }

    }

    public void decay(final @NotNull DecayStrategy decay) {

        synchronized (storage.pendingWriteLock()) {
            final double next = Math.max(0.0D, decay.decay(value));
            if (attached && storage.enabled()) {
                if (!loaded) {
                    /*
                     * A decay that leaves the provisional level at zero can still lower the stored level.
                     */
                    storage.enqueue(new StorageMutation.PendingViolation(playerUuid, check, decay::decay, now()), false);
                } else if (Double.compare(next, value) != 0) {
                    value = next;
                    save(false);
                    return;
                }
            }
            value = next;
        }

    }

    public void reset() {

        synchronized (storage.pendingWriteLock()) {
            value = 0.0D;
            if (attached && storage.enabled()) {
                if (loaded) {
                    save(false);
                } else {
                    storage.enqueue(new StorageMutation.PendingViolation(playerUuid, check, base -> 0.0D, now()), false);
                }
            }
        }

    }

    @NotNull String check() {

        return check;

    }

    void load(final @Nullable StoredViolation stored) {

        if (attached && !loaded) {
            value = stored == null ? 0.0D : Math.max(0.0D, stored.value());
            updatedAt = stored == null ? 0L : stored.updatedAt();
            loaded = true;
        }

    }

    void saveSnapshot(final boolean allowClosing) {

        synchronized (storage.pendingWriteLock()) {
            if (attached && loaded && storage.enabled()) {
                save(allowClosing);
            }
        }

    }

    void detach() {

        synchronized (storage.pendingWriteLock()) {
            try {
                saveSnapshot(false);
            } finally {
                attached = false;
            }
        }

    }

    private void save(final boolean allowClosing) {

        storage.enqueue(new StorageMutation.Violation(playerUuid, check, value, now()), allowClosing);

    }

    private long now() {

        updatedAt = Math.max(updatedAt, System.currentTimeMillis());
        return updatedAt;

    }

}
