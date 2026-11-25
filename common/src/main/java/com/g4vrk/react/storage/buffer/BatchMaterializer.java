package com.g4vrk.react.storage.buffer;

import com.g4vrk.react.storage.model.PlayerStorageData;
import com.g4vrk.react.storage.model.StorageMutation;
import com.g4vrk.react.storage.model.StoredViolation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves relative violation increments in a queued batch against stored state and
 * collapses superseded writes for the same player and check.
 */
public final class BatchMaterializer {

    private BatchMaterializer() {

        throw new UnsupportedOperationException("Utility class");

    }

    public static @NotNull List<StorageMutation> materialize(
            final @NotNull List<StorageMutation> batch,
            final @NotNull BackendLoader loader
    ) throws Exception {

        Map<UUID, Map<String, BatchViolation>> levels = null;
        for (final StorageMutation mutation : batch) {
            if (mutation instanceof final StorageMutation.PendingViolation pending) {
                if (levels == null) {
                    levels = new HashMap<>();
                }
                if (!levels.containsKey(pending.playerUuid())) {
                    final PlayerStorageData stored = loader.load(pending.playerUuid());
                    final Map<String, BatchViolation> base = new HashMap<>();
                    stored.violations().forEach((check, value) -> base.put(check, new BatchViolation(value, -1)));
                    levels.put(pending.playerUuid(), base);
                }
            }
        }
        if (levels == null) {
            return batch;
        }

        /*
         * Keep the latest materialized write per check at its original position in the batch.
         */
        final List<StorageMutation> materialized = new ArrayList<>(batch);
        for (int index = 0; index < batch.size(); index++) {
            final StorageMutation mutation = batch.get(index);
            final Map<String, BatchViolation> current = levels.get(mutation.playerUuid());
            if (current == null) {
                continue;
            }
            final StorageMutation.Violation violation;
            if (mutation instanceof final StorageMutation.PendingViolation pending) {
                final BatchViolation previous = current.get(pending.check());
                violation = pending.materialize(previous == null ? null : previous.value());
            } else if (mutation instanceof final StorageMutation.Violation absolute) {
                violation = absolute;
            } else {
                continue;
            }
            final BatchViolation previous = current.get(violation.check());
            if (previous == null || violation.updatedAt() >= previous.value().updatedAt()) {
                if (previous != null && previous.index() >= 0) {
                    materialized.set(previous.index(), null);
                }
                current.put(violation.check(), new BatchViolation(new StoredViolation(violation.violations(), violation.updatedAt()), index));
                materialized.set(index, violation);
            } else {
                materialized.set(index, null);
            }
        }
        materialized.removeIf(Objects::isNull);
        return materialized;

    }

    /**
     * Loads the baseline persisted data needed to resolve queued relative deltas for one player.
     */
    @FunctionalInterface
    public interface BackendLoader {

        @NotNull PlayerStorageData load(
                final @NotNull UUID playerUuid
        ) throws Exception;

    }

    /**
     * A recovered check level and the position of its pending absolute write, if any.
     */
    private record BatchViolation(
            @NotNull StoredViolation value,
            int index
    ) {

    }

}
