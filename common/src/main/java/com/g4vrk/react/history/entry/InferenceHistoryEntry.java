package com.g4vrk.react.history.entry;

import com.g4vrk.react.check.Check;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.lang.System.currentTimeMillis;

/**
 * An inference result retained for display and persistence.
 */
@Getter
public final class InferenceHistoryEntry {

    private final long timestamp;

    private final Check check;

    private final double probability;

    private final @Nullable String modelId;

    private final @Nullable String runId;

    private final @Nullable Double threshold;

    private final @Nullable Boolean enforcement;

    private final @Nullable Double violationsBefore;

    private final @Nullable Double violationsAfter;

    public InferenceHistoryEntry(
            final @NotNull Check check,
            final double probability
    ) {

        this(currentTimeMillis(), check, probability, null, null, null, null, null, null);

    }

    public InferenceHistoryEntry(
            final long timestamp,
            final @NotNull Check check,
            final double probability
    ) {

        this(timestamp, check, probability, null, null, null, null, null, null);

    }

    public InferenceHistoryEntry(
            final @NotNull Check check,
            final double probability,
            final @Nullable String modelId,
            final @Nullable String runId,
            final @Nullable Double threshold,
            final @Nullable Boolean enforcement,
            final @Nullable Double violationsBefore,
            final @Nullable Double violationsAfter
    ) {

        this(currentTimeMillis(), check, probability, modelId, runId, threshold, enforcement,
                violationsBefore, violationsAfter);

    }

    public InferenceHistoryEntry(
            final long timestamp,
            final @NotNull Check check,
            final double probability,
            final @Nullable String modelId,
            final @Nullable String runId,
            final @Nullable Double threshold,
            final @Nullable Boolean enforcement,
            final @Nullable Double violationsBefore,
            final @Nullable Double violationsAfter
    ) {

        this.timestamp = timestamp;
        this.check = check;
        this.probability = probability;
        this.modelId = modelId;
        this.runId = runId;
        this.threshold = threshold;
        this.enforcement = enforcement;
        this.violationsBefore = violationsBefore;
        this.violationsAfter = violationsAfter;

    }

}
