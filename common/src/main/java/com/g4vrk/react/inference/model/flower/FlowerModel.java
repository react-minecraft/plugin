package com.g4vrk.react.inference.model.flower;

import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.inference.aim.InferenceResult;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.model.flower.protocol.FlowerCodec;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.model.rotation.Rotation;
import com.g4vrk.react.player.model.rotation.RotationData;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/** Flower models read the last rotations, sent as FlatBuffers (protocol/flower). */
public final class FlowerModel implements AimModel {

    private final ModelId id;
    // Rotations since the last request, per player; each model keeps its own request step.
    private final Map<UUID, AtomicInteger> samples = new ConcurrentHashMap<>();

    public FlowerModel(final @NotNull ModelId id) {
        this.id = id;
    }

    @Override
    public @NotNull ModelId id() {
        return id;
    }

    @Override
    public void onRotation(final @NotNull ReactPlayer player, final @NotNull RotationData data, final @NotNull AimAI check) {
        if (!player.combatActivity.isActive()) {
            return;
        }
        final AtomicInteger counter = samples.computeIfAbsent(player.getUniqueId(), ignored -> new AtomicInteger());
        if (counter.incrementAndGet() < id.step() || data.historySize() < id.window() || !check.tryStart(this)) {
            return;
        }
        final Rotation[] history = data.snapshotHistory();
        if (history.length < id.window()) {
            check.cancel(this);
            return;
        }
        counter.set(0);
        final Rotation[] snapshot = Arrays.copyOfRange(history, history.length - id.window(), history.length);
        final String playerName = player.getName();
        check.send(this, token -> new HttpRequest(FlowerCodec.encodeRequest(snapshot, playerName, token)));
    }

    @Override
    public void onReload(final @NotNull ReactPlayer player, final @NotNull AimAI check) {
        samples.remove(player.getUniqueId());
    }

    @Override
    public @NotNull InferenceResult readResponse(final byte @NotNull [] body) {
        // No decision threshold: aim-ai.yml alert.threshold decides.
        return InferenceResult.of(FlowerCodec.decodeProbability(body), Double.NaN, true);
    }

    @Override
    public void resetPlayer(final @NotNull UUID player) {
        samples.remove(player);
    }

}
