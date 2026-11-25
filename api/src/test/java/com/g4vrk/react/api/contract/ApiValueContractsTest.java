/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.api.contract;

import com.g4vrk.react.api.addon.meta.AddonMetadata;
import com.g4vrk.react.api.extension.meta.ExtensionId;
import com.g4vrk.react.api.platform.AimSnapshot;
import com.g4vrk.react.api.punish.PunishmentRule;
import com.g4vrk.react.api.rcp.player.RotationSample;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies immutability, bounds validation, and defensive copies across React's API value types.
 */
final class ApiValueContractsTest {

    @Test
    void inferenceResultRejectsOutOfRangeOrMalformedInputs() {

        assertThrows(NullPointerException.class, () -> new InferenceResult(null, "Name", 0.5D, 0.8D, true));
        assertThrows(NullPointerException.class, () -> new InferenceResult("m/1", null, 0.5D, 0.8D, true));

        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", -0.01D, 0.5D, true));
        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", 1.01D, 0.5D, true));
        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", Double.NaN, 0.5D, true));
        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", Double.POSITIVE_INFINITY, 0.5D, true));

        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", 0.5D, -0.01D, true));
        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", 0.5D, 1.01D, true));
        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", 0.5D, Double.NEGATIVE_INFINITY, true));

        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", 0.5D, 0.5D, true, "   "));
        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", 0.5D, 0.5D, true, "a".repeat(1025)));
        assertThrows(IllegalArgumentException.class, () -> new InferenceResult("m/1", "M", 0.5D, 0.5D, true, "bad\nrun"));

        final InferenceResult configThresholdFallback = new InferenceResult("m/1", "M", 0.0D, Double.NaN, false, "run-1");
        assertTrue(Double.isNaN(configThresholdFallback.threshold()));
        assertFalse(configThresholdFallback.hasThreshold());

    }

    @Test
    void aimSnapshotEnforcesValidNumericAndNullBounds() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new AimSnapshot(0, 64, 0, 1.62, false, 0, 0, 0, 0, 0, 0, -1, "minecraft:air", false, false, 10.0f, 769)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AimSnapshot(0, 64, 0, 1.62, false, 0, 0, 0, 0, 0, 0, 0, "minecraft:air", false, false, -1.0f, 769)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AimSnapshot(0, 64, 0, 1.62, false, 0, 0, 0, 0, 0, 0, 0, "minecraft:air", false, false, Float.NaN, 769)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AimSnapshot(0, 64, 0, 1.62, false, 0, 0, 0, 0, 0, 0, 0, "minecraft:air", false, false, 10.0f, -2)
        );
        assertThrows(
                NullPointerException.class,
                () -> new AimSnapshot(0, 64, 0, 1.62, false, 0, 0, 0, 0, 0, 0, 0, null, false, false, 10.0f, -1)
        );

    }

    @Test
    void rotationSampleEnforcesStrideAndBounds() {

        assertEquals(8, RotationSample.STRIDE);
        final float[] raw = {10F, 20F, 1F, 2F, 0.1F, 0.2F, 0.01F, 0.02F, 15F, 25F, 5F, 5F, 4F, 3F, 0.03F, 0.04F};
        assertEquals(2, RotationSample.count(raw));
        assertEquals(15F, RotationSample.pitch(raw, 1));
        assertEquals(25F, RotationSample.yaw(raw, 1));
        assertEquals(5F, RotationSample.deltaPitch(raw, 1));
        assertEquals(5F, RotationSample.deltaYaw(raw, 1));

        assertThrows(IndexOutOfBoundsException.class, () -> RotationSample.value(raw, -1, RotationSample.PITCH));
        assertThrows(IndexOutOfBoundsException.class, () -> RotationSample.value(raw, 2, RotationSample.PITCH));
        assertThrows(IndexOutOfBoundsException.class, () -> RotationSample.value(raw, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> RotationSample.value(raw, 0, RotationSample.STRIDE));

    }

    @Test
    void extensionIdAndMetadataRecordsAreImmutableAndValidated() {

        assertEquals("addon:menu", ExtensionId.addon("menu").toString());
        assertEquals("rcp:inference", ExtensionId.rcp("inference").toString());
        assertThrows(IllegalArgumentException.class, () -> new ExtensionId("Addon", "menu"));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionId("addon", "../bad"));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionId("addon", ""));

        final List<String> mutableLines = new ArrayList<>(List.of("[alert]"));
        final PunishmentRule rule = new PunishmentRule(5, 5, mutableLines);
        mutableLines.add("[console] ban <player>");
        assertEquals(List.of("[alert]"), rule.lines());
        assertThrows(UnsupportedOperationException.class, () -> rule.lines().add("[log]"));
        assertTrue(rule.repeating());
        assertFalse(new PunishmentRule(10, 0, List.of()).repeating());

        final List<String> authors = new ArrayList<>(List.of("g4vrk"));
        final List<String> contributors = new ArrayList<>(List.of("helper"));
        final AddonMetadata metadata = new AddonMetadata("test", "1.0.0", "com.example.Test", 2, "desc", authors, contributors, null);
        authors.clear();
        contributors.clear();
        assertEquals(List.of("g4vrk"), List.copyOf(metadata.authors()));
        assertEquals(List.of("helper"), List.copyOf(metadata.contributors()));

    }

}
