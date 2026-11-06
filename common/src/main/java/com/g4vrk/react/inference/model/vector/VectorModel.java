package com.g4vrk.react.inference.model.vector;

import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.inference.aim.InferenceResult;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.model.vector.listener.VectorListener;
import com.g4vrk.react.inference.model.vector.model.AimFrame;
import com.g4vrk.react.inference.model.vector.protocol.FrameCodec;
import com.g4vrk.react.inference.model.vector.service.AimStreamService;
import com.g4vrk.react.inference.model.vector.session.AimSession;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Vector Preview: aim frames collected from packets during combat, sent as FlatBuffers. */
public final class VectorModel implements AimModel {

    private static final MediaType FLATBUFFERS = MediaType.get("application/x-flatbuffers");

    private final PlayerRegistry players;
    private final AimStreamService stream;

    public VectorModel(final @NotNull PlayerRegistry players, final @NotNull Scheduler scheduler) {
        this.players = players;
        this.stream = new AimStreamService(players, scheduler, this::onFrame);
    }

    @Override
    public @NotNull ModelId id() {
        return ModelId.VECTOR_PREVIEW;
    }

    public @NotNull AimStreamService stream() {
        return stream;
    }

    @Override
    public @NotNull List<PacketListenerCommon> packetListeners() {
        return List.of(new VectorListener(players, stream));
    }

    private void onFrame(final @NotNull ReactPlayer player, final @NotNull AimSession session) {
        final AimAI check = player.checkManager.getCheck(AimAI.class);
        if (check == null || !session.combatActivity.isActive()) {
            return;
        }
        session.resize(check.bufferSequence());
        if (!check.tryStart(this)) {
            return;
        }
        final AimFrame[] snapshot = session.pollSnapshot(check.bufferStep());
        if (snapshot == null) {
            check.cancel(this);
            return;
        }
        final String playerName = player.getName();
        check.send(this, token -> new HttpRequest("POST",
                RequestBody.create(FrameCodec.encodeBatch(snapshot, playerName, token), FLATBUFFERS),
                Map.of("Accept", "application/x-flatbuffers", "X-Batch", "1")));
    }

    @Override
    public void onReload(final @NotNull ReactPlayer player, final @NotNull AimAI check) {
        stream.claimPlayerData(player.getUniqueId()).resize(check.bufferSequence());
    }

    @Override
    public @Nullable InferenceResult readBinary(final byte @NotNull [] body, final @NotNull Response response) {
        if (body.length < 4 || body[0] != 'G' || body[1] != 'A' || body[2] != 'I' || body[3] != 'B') {
            return null;
        }
        final FrameCodec.Prediction prediction = FrameCodec.decodePrediction(body);
        return new InferenceResult(prediction.probability(), prediction.threshold(), prediction.enforcementEnabled(),
                response.header("X-React-Model-Family"), response.header("X-React-Model-Name"));
    }

    @Override
    public void clearHistory(final @NotNull UUID player) {
        stream.claimPlayerData(player).clearHistory();
    }

    @Override
    public void resetPlayer(final @NotNull UUID player) {
        stream.claimPlayerData(player).reset();
    }

    @Override
    public void removePlayer(final @NotNull UUID player) {
        stream.removePlayerData(player);
    }

}
