package com.g4vrk.react.inference.model;

import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.inference.aim.InferenceResult;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.model.rotation.RotationData;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import java.util.List;
import java.util.UUID;
import java.net.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One server model the Aim-AI check can ask. The check talks to every model through this
 * interface only: what a model collects, how it encodes a window and reads an answer stays
 * inside the model's own package.
 */
public interface AimModel {

    @NotNull ModelId id();

    /** Packet listeners the model needs to collect its input. */
    default @NotNull List<PacketListenerCommon> packetListeners() {
        return List.of();
    }

    /** Called after every rotation the plugin recorded for the player. */
    default void onRotation(final @NotNull ReactPlayer player, final @NotNull RotationData data, final @NotNull AimAI check) {
    }

    /** Called when aim-ai.yml is reloaded for the player. */
    default void onReload(final @NotNull ReactPlayer player, final @NotNull AimAI check) {
    }

    /** Reads a binary answer; null when the body is not in this model's format. */
    default @Nullable InferenceResult readBinary(final byte @NotNull [] body, final @NotNull HttpResponse<?> response) {
        return null;
    }

    /** Whether an answer may come without a decision threshold, leaving it to aim-ai.yml. */
    default boolean usesLocalThreshold() {
        return false;
    }

    /** The server or endpoint changed: windows collected so far no longer apply. */
    default void clearHistory(final @NotNull UUID player) {
    }

    /** The player's transient state is reset. */
    default void resetPlayer(final @NotNull UUID player) {
    }

    /** The player left; everything kept for them is dropped. */
    default void removePlayer(final @NotNull UUID player) {
        resetPlayer(player);
    }

}
