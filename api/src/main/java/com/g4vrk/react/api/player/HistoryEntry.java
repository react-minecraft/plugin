package com.g4vrk.react.api.player;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One model verdict in a player's history.
 *
 * @param timestamp   when it was recorded, in epoch milliseconds
 * @param check       the id of the check that produced it
 * @param probability the cheating probability, from {@code 0} to {@code 1}
 * @param modelId     the stable model identifier, or {@code null} for older history
 * @param runId       the model version used for inference, when supplied
 * @param threshold   the model threshold, when supplied
 * @param enforcement whether the result affected the violation level, or {@code null} for older history
 * @param violationsBefore violation level before processing the result, when known
 * @param violationsAfter  violation level after processing the result, when known
 */
@ApiStatus.AvailableSince("2.0")
public record HistoryEntry(
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

    public HistoryEntry(final long timestamp, final @NotNull String check, final double probability) {

        this(timestamp, check, probability, null, null, null, null, null, null);

    }

}
