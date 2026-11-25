package com.g4vrk.react.check.processor.rotation;

import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.api.rcp.player.RotationSample;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.rotation.RotationData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Records one movement packet as a rotation and hands the new sample to the aim check.
 *
 * <p>Each packet thread reuses a sample buffer so a concurrent reset cannot change the sample
 * while an RCP reads it.</p>
 */
public final class RotationProcessor {

    private volatile @Nullable ThreadLocal<float[]> sample = new ThreadLocal<>();

    public void process(
            final @NotNull TrackedPlayerImpl player,
            final float yaw,
            final float pitch
    ) {

        final ThreadLocal<float[]> local = this.sample;
        if (local == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }

        final RotationData rotationData = player.rotationData();
        float[] current = local.get();
        if (current == null) {
            current = new float[RotationSample.STRIDE];
            local.set(current);
        }
        synchronized (rotationData) {
            rotationData.push(yaw, pitch);
            System.arraycopy(rotationData.current(), 0, current, 0, RotationSample.STRIDE);
        }

        final AimAI check = player.check(AimAI.class);

        if (check != null) {
            check.onRotation(current);
        }

    }

    public void close() {

        final ThreadLocal<float[]> current = this.sample;
        this.sample = null;
        if (current != null) {
            current.remove();
        }

    }

}
