package com.g4vrk.react.api.rcp.verdict;

import java.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a model concluded about one window of a player's aim.
 *
 * @param modelId     stable model id, for example {@code flower/baseline}
 * @param displayName the name players and staff see, for example {@code Flower Baseline}
 * @param probability probability of cheating, from 0 to 1
 * @param threshold   probability from 0 to 1 at which the player is flagged; {@link Double#NaN} leaves
 *                    the decision to the alert threshold of the plugin's check config
 * @param enforcement false when the result is only observed: it is shown and stored,
 *                    but never flags or rewards the player
 * @param runId       backend model run identifier, when supplied
 */
@ApiStatus.AvailableSince("2.0")
public record InferenceResult(
        @NotNull String modelId,
        @NotNull String displayName,
        double probability,
        double threshold,
        boolean enforcement,
        @Nullable String runId
) {

    public InferenceResult(
            final @NotNull String modelId,
            final @NotNull String displayName,
            final double probability,
            final double threshold,
            final boolean enforcement
    ) {

        this(modelId, displayName, probability, threshold, enforcement, null);

    }

    public InferenceResult {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(displayName, "displayName");
        if (!(probability >= 0.0D && probability <= 1.0D)) {
            throw new IllegalArgumentException("probability must be within [0, 1], got " + probability);
        }
        if (!Double.isNaN(threshold) && !(threshold >= 0.0D && threshold <= 1.0D)) {
            throw new IllegalArgumentException("threshold must be NaN or within [0, 1], got " + threshold);
        }
        if (runId != null) {
            if (runId.isBlank() || runId.length() > 1024) {
                throw new IllegalArgumentException("runId must be nonblank and contain at most 1024 characters");
            }
            for (int index = 0; index < runId.length(); index++) {
                if (Character.isISOControl(runId.charAt(index))) {
                    throw new IllegalArgumentException("runId must not contain control characters");
                }
            }
        }
    }

    public boolean hasThreshold() {

        return Double.isFinite(threshold);

    }

}
