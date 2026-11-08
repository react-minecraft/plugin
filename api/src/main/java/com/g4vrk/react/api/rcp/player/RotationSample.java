package com.g4vrk.react.api.rcp.player;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Layout of one head rotation inside the flat {@code float[]} the plugin hands out.
 *
 * <p>The plugin records rotations on the packet thread, so it never creates an object per
 * rotation. A sample is {@value #STRIDE} floats in a row, laid out as</p>
 *
 * <p>{@code [pitch, yaw, deltaPitch, deltaYaw, jerkPitch, jerkYaw, gcdErrorPitch, gcdErrorYaw]}</p>
 *
 * <p>Deltas, jerks and GCD errors are measured against the previous rotation of the same
 * player; the first rotation of a session has zeroes for all of them.</p>
 */
@ApiStatus.AvailableSince("2.0")
public final class RotationSample {

    /**
     * How many floats one rotation takes.
     */
    public static final int STRIDE = 8;

    public static final int PITCH = 0;
    public static final int YAW = 1;
    public static final int DELTA_PITCH = 2;
    public static final int DELTA_YAW = 3;
    public static final int JERK_PITCH = 4;
    public static final int JERK_YAW = 5;
    public static final int GCD_ERROR_PITCH = 6;
    public static final int GCD_ERROR_YAW = 7;

    private RotationSample() {
    }

    /**
     * Counts the rotations held by a flat array.
     *
     * @param samples a flat array built by the plugin
     * @return how many rotations it holds
     */
    public static int count(final float @NotNull [] samples) {
        return samples.length / STRIDE;
    }

    /**
     * Reads one value of one rotation.
     *
     * @param samples the flat array
     * @param index   which rotation, counted from {@code 0}
     * @param field   one of the field constants of this class
     * @return the value
     * @throws IndexOutOfBoundsException when the rotation is not in the array
     */
    public static float value(final float @NotNull [] samples, final int index, final int field) {
        return samples[index * STRIDE + field];
    }

    /**
     * Reads the pitch of one rotation.
     *
     * @param samples the flat array
     * @param index   which rotation, counted from {@code 0}
     * @return the pitch, in degrees
     */
    public static float pitch(final float @NotNull [] samples, final int index) {
        return value(samples, index, PITCH);
    }

    /**
     * Reads the yaw of one rotation.
     *
     * @param samples the flat array
     * @param index   which rotation, counted from {@code 0}
     * @return the yaw, in degrees
     */
    public static float yaw(final float @NotNull [] samples, final int index) {
        return value(samples, index, YAW);
    }

    /**
     * Reads the pitch change of one rotation.
     *
     * @param samples the flat array
     * @param index   which rotation, counted from {@code 0}
     * @return the change since the previous rotation, in degrees
     */
    public static float deltaPitch(final float @NotNull [] samples, final int index) {
        return value(samples, index, DELTA_PITCH);
    }

    /**
     * Reads the yaw change of one rotation.
     *
     * @param samples the flat array
     * @param index   which rotation, counted from {@code 0}
     * @return the change since the previous rotation, in degrees
     */
    public static float deltaYaw(final float @NotNull [] samples, final int index) {
        return value(samples, index, DELTA_YAW);
    }

}
