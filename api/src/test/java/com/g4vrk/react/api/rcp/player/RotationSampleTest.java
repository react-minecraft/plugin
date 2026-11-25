package com.g4vrk.react.api.rcp.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies bounds checking and packed sample layout in {@link RotationSample}.
 */
class RotationSampleTest {

    @Test
    void rejectsIndicesOutsideTheRotationAndField() {

        final float[] samples = new float[RotationSample.STRIDE * 2];
        samples[RotationSample.STRIDE] = 7.0F;

        assertEquals(7.0F, RotationSample.value(samples, 1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> RotationSample.value(samples, -1, RotationSample.STRIDE));
        assertThrows(IndexOutOfBoundsException.class, () -> RotationSample.value(samples, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> RotationSample.value(samples, 0, RotationSample.STRIDE));
        assertThrows(IndexOutOfBoundsException.class, () -> RotationSample.value(samples, 2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> RotationSample.value(samples, Integer.MAX_VALUE, 0));

    }

}
