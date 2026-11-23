package com.g4vrk.react.api.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests backwards-compatible access to inference history metadata.
 */
class HistoryEntryTest {

    @Test
    void preservesLegacyConstructorAndAcceptsDetailedHistory() {

        final HistoryEntry legacy = new HistoryEntry(10L, "aim-ai", 0.75D);
        assertNull(legacy.modelId());
        assertNull(legacy.runId());
        assertNull(legacy.enforcement());

        final HistoryEntry detailed = new HistoryEntry(
                11L, "aim-ai", 0.8D, "flower/baseline", "run-42", 0.7D, false, 1.0D, 1.0D);
        assertEquals("flower/baseline", detailed.modelId());
        assertEquals("run-42", detailed.runId());
        assertEquals(0.7D, detailed.threshold());
        assertEquals(false, detailed.enforcement());
        assertEquals(1.0D, detailed.violationsBefore());
        assertEquals(1.0D, detailed.violationsAfter());

    }

}
