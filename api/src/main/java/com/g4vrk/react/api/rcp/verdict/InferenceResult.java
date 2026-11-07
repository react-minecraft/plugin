package com.g4vrk.react.api.rcp.verdict;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * What a model concluded about one window of a player's aim.
 *
 * @param modelId     stable model id, for example {@code flower/baseline}
 * @param displayName the name players and staff see, for example {@code Flower Baseline}
 * @param probability probability of cheating, from 0 to 1
 * @param threshold   probability at which the player is flagged; {@link Double#NaN} leaves
 *                    the decision to the alert threshold of the plugin's check config
 * @param enforcement false when the result is only observed: it is shown and stored,
 *                    but never flags or rewards the player
 */
public record InferenceResult(
        @NotNull String modelId,
        @NotNull String displayName,
        double probability,
        double threshold,
        boolean enforcement
) {

    public InferenceResult {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(displayName, "displayName");
        if (!(probability >= 0.0D && probability <= 1.0D)) {
            throw new IllegalArgumentException("probability must be within [0, 1], got " + probability);
        }
    }

    public boolean hasThreshold() {
        return Double.isFinite(threshold);
    }

}
