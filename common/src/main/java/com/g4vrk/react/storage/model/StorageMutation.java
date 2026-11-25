package com.g4vrk.react.storage.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.DoubleUnaryOperator;

/**
 * A queued change to persistent player data.
 */
public interface StorageMutation {

    @NotNull UUID playerUuid();

    /**
     * Stores a check's violation level.
     */
    record Violation(
            @NotNull UUID playerUuid,
            @NotNull String check,
            double violations,
            long updatedAt
    ) implements StorageMutation {
    }

    /**
     * Changes a violation level whose stored base has not been read yet.
     */
    record PendingViolation(
            @NotNull UUID playerUuid,
            @NotNull String check,
            @NotNull DoubleUnaryOperator change,
            long updatedAt
    ) implements StorageMutation {

        public @NotNull Violation materialize(final @Nullable StoredViolation stored) {

            final double base = stored == null ? 0.0D : Math.max(0.0D, stored.value());
            final double changed = Math.max(0.0D, change.applyAsDouble(base));
            final long timestamp = stored == null ? updatedAt : Math.max(updatedAt, stored.updatedAt());
            return new Violation(playerUuid, check, Double.isFinite(changed) ? changed : base, timestamp);

        }

    }

    /**
     * Appends an inference result to player history.
     */
    record Inference(
            @NotNull UUID playerUuid,
            @NotNull UUID id,
            @NotNull StoredInference value
    ) implements StorageMutation {
    }

    /**
     * Grants or lifts a bypass.
     *
     * @param playerUuid the player
     * @param check      the check id, {@code *} for every check
     * @param until      when the bypass ends in epoch millis, {@link Long#MAX_VALUE} for never,
     *                   a negative value to lift it
     */
    record BypassMutation(
            @NotNull UUID playerUuid,
            @NotNull String check,
            long until
    ) implements StorageMutation {

        public boolean lifted() {

            return until < 0;

        }

    }
}
