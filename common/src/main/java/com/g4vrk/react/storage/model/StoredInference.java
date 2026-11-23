package com.g4vrk.react.storage.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persisted inference data; nullable metadata marks rows written before provenance was stored.
 */
public record StoredInference(
        long timestamp,
        @NotNull String check,
        double probability,
        @Nullable String modelId,
        @Nullable String runId,
        @Nullable Double threshold,
        @Nullable Boolean enforcement,
        @Nullable Double violationsBefore,
        @Nullable Double violationsAfter
) {

    public StoredInference(final long timestamp, final @NotNull String check, final double probability) {

        this(timestamp, check, probability, null, null, null, null, null, null);

    }

}
