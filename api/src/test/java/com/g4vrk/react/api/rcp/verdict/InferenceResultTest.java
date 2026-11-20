package com.g4vrk.react.api.rcp.verdict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InferenceResultTest {

    @Test
    void acceptsProbabilityThresholdsAndNanFallback() {

        assertFalse(result(Double.NaN).hasThreshold());
        assertTrue(result(0.0D).hasThreshold());
        assertTrue(result(1.0D).hasThreshold());

    }

    @Test
    void rejectsOutOfRangeAndInfiniteThresholds() {

        for (final double threshold : new double[]{-0.1D, 1.1D, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> result(threshold));
        }

    }

    private static InferenceResult result(final double threshold) {

        return new InferenceResult("model", "Model", 0.5D, threshold, true);

    }

}
