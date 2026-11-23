package com.g4vrk.react.check.impl.aim;

import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that observation results cannot alter violation state.
 */
final class AimAIEnforcementTest {

    @Test
    void observingFlaggedResultDoesNotFlag() {

        assertEquals(
                AimAI.ResultAction.OBSERVE,
                AimAI.resultAction(result(false), true)
        );

    }

    @Test
    void observingCleanResultDoesNotReward() {

        assertEquals(
                AimAI.ResultAction.OBSERVE,
                AimAI.resultAction(result(false), false)
        );

    }

    @Test
    void enforcedFlaggedResultFlags() {

        assertEquals(
                AimAI.ResultAction.FLAG,
                AimAI.resultAction(result(true), true)
        );

    }

    @Test
    void enforcedCleanResultRewards() {

        assertEquals(
                AimAI.ResultAction.REWARD,
                AimAI.resultAction(result(true), false)
        );

    }

    private static InferenceResult result(final boolean enforcement) {

        return new InferenceResult("flower/baseline", "Flower Baseline", 0.95D, 0.5D, enforcement);

    }

}
