package com.g4vrk.react.api.rcp.verdict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies threshold invariants and NaN fallback behavior in {@link InferenceResult}.
 */
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

    @Test
    void acceptsOptionalRunIdentifierAndKeepsLegacyConstructor() {

        assertEquals("run-42", new InferenceResult("model", "Model", 0.5D, 0.7D, true, "run-42").runId());
        assertEquals(null, new InferenceResult("model", "Model", 0.5D, 0.7D, true).runId());

    }

    @Test
    void rejectsInvalidRunIdentifiers() {

        assertThrows(IllegalArgumentException.class,
                () -> new InferenceResult("model", "Model", 0.5D, 0.7D, true, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new InferenceResult("model", "Model", 0.5D, 0.7D, true, "run\n42"));
        assertThrows(IllegalArgumentException.class,
                () -> new InferenceResult("model", "Model", 0.5D, 0.7D, true, "x".repeat(1025)));

    }

    private static InferenceResult result(final double threshold) {

        return new InferenceResult("model", "Model", 0.5D, threshold, true);

    }

}
