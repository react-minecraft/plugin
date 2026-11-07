package com.g4vrk.react.check.processor.rotation;

import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.model.rotation.RotationData;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public final class RotationProcessor {

    private final RotationFactory rotationFactory;

    public void process(
            final @NotNull TrackedPlayerImpl player,
            final float yaw,
            final float pitch
    ) {

        final RotationData rotationData = player.rotationData;

        final Rotation current;

        synchronized (rotationData) {
            current = this.rotationFactory.create(yaw, pitch, rotationData.current());

            rotationData.push(current);
        }

        final AimAI check = player.checkManager.getCheck(AimAI.class);

        if (check != null) {
            check.onRotation(current);
        }

    }

}
