package com.g4vrk.react.util.math;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AngleMath {

    private static final float GCD_EPSILON = 0.0001f;

    public static float calculateAcceleration(
            final float currentDelta,
            final float previousDelta
    ) {
        return currentDelta - previousDelta;
    }

    public static float calculateJerk(
            final float currentAccel,
            final float previousAccel
    ) {
        return currentAccel - previousAccel;
    }

    public static float calculateGCDError(
            final float delta
    ) {
        if (Math.abs(delta) < GCD_EPSILON) {
            return 0.0f;
        }

        float gcd = calculateGCD(Math.abs(delta));
        float remainder = Math.abs(delta) % gcd;

        return remainder / gcd;
    }

    private static float calculateGCD(
            final float a
    ) {
        float x = Math.abs(a);
        float b = 0.1f;

        while (b > GCD_EPSILON) {
            final float temp = x % b;
            x = b;
            b = temp;
        }

        return x;
    }

    public static float normalizeAngle(
            final float angle
    ) {
        float normalized = angle % 360.0f;
        if (normalized > 180.0f) {
            normalized -= 360.0f;
        } else if (normalized < -180.0f) {
            normalized += 360.0f;
        }
        return normalized;
    }

    public static float normalizeDelta(
            final float delta
    ) {
        float normalized = delta % 360.0f;

        if (normalized > 180.0f) {
            normalized -= 360.0f;
        } else if (normalized < -180.0f) {
            normalized += 360.0f;
        }

        return normalized;
    }
}
