package com.g4vrk.react.check.processor.rotation;

import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.rotation.RotationData;
import org.jetbrains.annotations.NotNull;

/**
 * Records one movement packet as a rotation and hands the new sample to the aim check.
 *
 * <p>Nothing here allocates: the sample is written into the player's flat history and reaches
 * the check as the {@link RotationData} it lives in.</p>
 */
public final class RotationProcessor {

    public void process(
            final @NotNull TrackedPlayerImpl player,
            final float yaw,
            final float pitch
    ) {

        final RotationData rotationData = player.rotationData;

        rotationData.push(yaw, pitch);

        final float[] current = rotationData.current();

        if (current == null) {
            return;
        }

        final AimAI check = player.checkManager.getCheck(AimAI.class);

        if (check != null) {
            check.onRotation(current);
        }

    }

}
