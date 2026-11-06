package com.g4vrk.react.inference.model.flower;

import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.model.rotation.Rotation;
import com.g4vrk.react.player.model.rotation.RotationData;
import com.g4vrk.react.inference.json.InferenceJsonCodec;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

/** Flower models read the last rotations as JSON, in the format of plugin version 1.3.0. */
public final class FlowerModel implements AimModel {

    private static final String JSON = "application/json; charset=utf-8";

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
        check.send(this, token -> {
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", playerName);
            payload.put("frames", snapshot);
            if (token != null) {
                payload.put("token", token);
            }
            return new HttpRequest("POST", InferenceJsonCodec.writeObject(payload).getBytes(StandardCharsets.UTF_8), JSON);
        });
    }

    @Override
    public void onReload(final @NotNull ReactPlayer player, final @NotNull AimAI check) {
        samples.remove(player.getUniqueId());
    }

    @Override
    public boolean usesLocalThreshold() {
        return true;
    }

    @Override
    public void resetPlayer(final @NotNull UUID player) {
        samples.remove(player);
    }

}
