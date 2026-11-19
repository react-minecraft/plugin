package com.g4vrk.react.storage.model;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A queued change to persistent player data.
 */
public sealed interface StorageMutation permits
        StorageMutation.Violation,
        StorageMutation.Inference,
        StorageMutation.Bypass {

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
    record Bypass(
            @NotNull UUID playerUuid,
            @NotNull String check,
            long until
    ) implements StorageMutation {

        public boolean lifted() {
            return until < 0;
        }

    }
}
