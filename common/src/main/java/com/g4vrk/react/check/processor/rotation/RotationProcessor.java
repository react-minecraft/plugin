package com.g4vrk.react.check.processor.rotation;

import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.api.rcp.player.RotationSample;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.rotation.RotationData;
import org.jetbrains.annotations.NotNull;

/**
 * Records one movement packet as a rotation and hands the new sample to the aim check.
 *
 * <p>Each packet thread reuses a sample buffer so a concurrent reset cannot change the sample
 * while an RCP reads it.</p>
 */
public final class RotationProcessor {

    private final ThreadLocal<float[]> sample = ThreadLocal.withInitial(() -> new float[RotationSample.STRIDE]);

    public void process(
            final @NotNull TrackedPlayerImpl player,
            final float yaw,
            final float pitch
    ) {

        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }

        final RotationData rotationData = player.rotationData;

        final float[] current = sample.get();
        synchronized (rotationData) {
            rotationData.push(yaw, pitch);
            System.arraycopy(rotationData.current(), 0, current, 0, RotationSample.STRIDE);
        }

        final AimAI check = player.checkManager.getCheck(AimAI.class);

        if (check != null) {
            check.onRotation(current);
        }

    }

}
