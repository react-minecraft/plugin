package com.g4vrk.react.player.rotation;

import com.g4vrk.react.api.rcp.player.RotationSample;
import com.g4vrk.react.util.math.AngleMath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The rotations of one player, kept as plain floats.
 *
 * <p>Rotations arrive with every movement packet, so this class never creates an object per
 * rotation: the current and the previous sample live in two reusable arrays and the history is
 * one flat ring buffer of {@link RotationSample#STRIDE} floats per sample.</p>
 *
 * <p>Every method is thread-safe; the packet thread writes, check and RCP threads read.</p>
 */
public final class RotationData {

    private final float[] history;
    private final int capacity;

    private final float[] current = new float[RotationSample.STRIDE];
    private final float[] previous = new float[RotationSample.STRIDE];

    private boolean hasCurrent;
    private boolean hasPrevious;

    private int head;
    private int size;

    public RotationData(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be higher than 0");
        }

        this.capacity = capacity;
        this.history = new float[capacity * RotationSample.STRIDE];
    }

    /**
     * Records a rotation and moves the previous one back.
     *
     * @param yaw   the yaw of the movement packet, in degrees
     * @param pitch the pitch of the movement packet, in degrees
     */
    public synchronized void push(final float yaw, final float pitch) {
        final float normalizedYaw = AngleMath.normalizeAngle(yaw);
        final float normalizedPitch = AngleMath.normalizeAngle(pitch);

        final float deltaYaw = this.hasCurrent
                ? AngleMath.normalizeDelta(normalizedYaw - this.current[RotationSample.YAW])
                : 0F;
        final float deltaPitch = this.hasCurrent
                ? AngleMath.normalizeDelta(normalizedPitch - this.current[RotationSample.PITCH])
                : 0F;

        final float jerkYaw = this.hasCurrent
                ? AngleMath.calculateAcceleration(deltaYaw, this.current[RotationSample.DELTA_YAW])
                : 0F;
        final float jerkPitch = this.hasCurrent
                ? AngleMath.calculateAcceleration(deltaPitch, this.current[RotationSample.DELTA_PITCH])
                : 0F;

        System.arraycopy(this.current, 0, this.previous, 0, RotationSample.STRIDE);
        this.hasPrevious = this.hasCurrent;

        this.current[RotationSample.PITCH] = normalizedPitch;
        this.current[RotationSample.YAW] = normalizedYaw;
        this.current[RotationSample.DELTA_PITCH] = deltaPitch;
        this.current[RotationSample.DELTA_YAW] = deltaYaw;
        this.current[RotationSample.JERK_PITCH] = jerkPitch;
        this.current[RotationSample.JERK_YAW] = jerkYaw;
        this.current[RotationSample.GCD_ERROR_PITCH] = AngleMath.calculateGCDError(deltaPitch);
        this.current[RotationSample.GCD_ERROR_YAW] = AngleMath.calculateGCDError(deltaYaw);

        this.hasCurrent = true;

        final int offset = this.head * RotationSample.STRIDE;
        System.arraycopy(this.current, 0, this.history, offset, RotationSample.STRIDE);

        this.head = (this.head + 1) % this.capacity;

        if (this.size < this.capacity) {
            this.size++;
        }
    }

    /**
     * The rotation recorded last.
     *
     * <p>The array is reused by every {@link #push(float, float)}, so read it inside the call
     * that got it and copy it when the values must outlive it.</p>
     *
     * @return the current sample, or {@code null} before the first rotation
     */
    public synchronized float @Nullable [] current() {
        return this.hasCurrent ? this.current : null;
    }

    /**
     * The rotation recorded before {@link #current()}.
     *
     * @return the previous sample, or {@code null} when there is none
     */
    public synchronized float @Nullable [] previous() {
        return this.hasPrevious ? this.previous : null;
    }

    /**
     * Copies the history, oldest first.
     *
     * @return one flat array of {@link RotationSample#STRIDE} floats per rotation
     */
    public synchronized float @NotNull [] history() {
        final float[] out = new float[this.size * RotationSample.STRIDE];

        final int start = (this.head - this.size + this.capacity) % this.capacity;

        for (int i = 0; i < this.size; i++) {
            System.arraycopy(
                    this.history,
                    ((start + i) % this.capacity) * RotationSample.STRIDE,
                    out,
                    i * RotationSample.STRIDE,
                    RotationSample.STRIDE
            );
        }

        return out;
    }

    /**
     * Counts the rotations kept.
     *
     * @return how many rotations the history holds
     */
    public synchronized int historySize() {
        return this.size;
    }

    /**
     * Counts the rotations the history can keep.
     *
     * @return the capacity
     */
    public synchronized int historyCapacity() {
        return this.capacity;
    }

    /**
     * Drops every rotation.
     */
    public synchronized void clear() {
        Arrays.fill(this.history, 0F);

        Arrays.fill(this.current, 0F);
        Arrays.fill(this.previous, 0F);

        this.hasCurrent = false;
        this.hasPrevious = false;

        this.head = 0;
        this.size = 0;
    }

}
