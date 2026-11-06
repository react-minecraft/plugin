package com.g4vrk.react.inference.model;

import com.g4vrk.react.inference.model.flower.FlowerModel;
import com.g4vrk.react.inference.model.vector.VectorModel;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/** The models this plugin build can run. Adding a model means adding it here, nothing else. */
public final class AimModels {

    private final List<AimModel> models;

    public AimModels(final @NotNull List<AimModel> models) {
        this.models = List.copyOf(models);
    }

    public static @NotNull AimModels create(final @NotNull PlayerRegistry players, final @NotNull Scheduler scheduler) {
        return new AimModels(List.of(
                new FlowerModel(ModelId.FLOWER_BASELINE),
                new FlowerModel(ModelId.FLOWER_ULTRA),
                new VectorModel(players, scheduler)
        ));
    }

    public @NotNull List<AimModel> all() {
        return models;
    }

    public @NotNull PacketListenerCommon @NotNull [] packetListeners() {
        return models.stream().flatMap(model -> model.packetListeners().stream()).toArray(PacketListenerCommon[]::new);
    }

    public void clearHistory(final @NotNull UUID player) {
        models.forEach(model -> model.clearHistory(player));
    }

    public void resetPlayer(final @NotNull UUID player) {
        models.forEach(model -> model.resetPlayer(player));
    }

    public void removePlayer(final @NotNull UUID player) {
        models.forEach(model -> model.removePlayer(player));
    }

}
