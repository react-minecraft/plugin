package com.g4vrk.react.check.impl.aim;

import com.g4vrk.config.Config;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.event.InferenceHistoryEntryAddedEvent;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.check.debug.DebugHandler;
import com.g4vrk.react.check.decay.DecayStrategy;
import com.g4vrk.react.check.decay.impl.LinearDecay;
import com.g4vrk.react.check.info.CheckInfo;
import com.g4vrk.react.color.resolver.ValueColorResolver;
import com.g4vrk.react.color.resolver.impl.ProbabilityColorResolver;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.aim.InferenceAimProcessor;
import com.g4vrk.react.inference.aim.InferenceResult;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.model.rotation.RotationData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import com.g4vrk.schedula.task.TickSchedule;
import java.util.concurrent.atomic.AtomicLong;
import com.g4vrk.react.inference.ProbabilityFormatter;

@CheckInfo(
        name = "Aim-AI",
        configId = "aim-ai"
)
public final class AimAI extends Check implements ReloadObserver {

    private final ValueColorResolver colorResolver;

    private final DebugHandler debugHandler;

    private InferenceAimProcessor inferenceAimProcessor;

    private boolean debug;

    private int sampleWindowSize;
    private int requestStep;
    private double alertThreshold;

    // Each model has one request in flight at a time, independently of the others.
    private final Map<ModelId, AtomicBoolean> requesting = new EnumMap<>(ModelId.class);
    private final AtomicLong requestGeneration = new AtomicLong();

    private DecayStrategy decayStrategy;

    public AimAI(@NotNull ReactPlayer player) {
        super(player);

        for (final ModelId model : ModelId.values()) {
            requesting.put(model, new AtomicBoolean());
        }

        this.inferenceAimProcessor = React.INSTANCE.getInferenceAimProcessor();
        this.colorResolver = new ProbabilityColorResolver();

        this.debugHandler = new DebugHandler(this);
    }

    @Override
    public void onReload(@NotNull Config config) {

        this.debug = config.node("debug").getBoolean();

        this.sampleWindowSize = Math.max(1, config.node("buffer", "sequence").getInt(64));
        this.requestStep = Math.max(1, config.node("buffer", "step").getInt(32));
        resetSamples();
        this.alertThreshold = config.node("alert", "threshold").getDouble(0.49D);

        final double decayAmount = config.node("decay", "amount").getDouble(0.5D);

        this.decayStrategy = new LinearDecay(decayAmount);

        this.inferenceAimProcessor = new InferenceAimProcessor(React.INSTANCE.getLogger(),
                React.INSTANCE.getInferenceServer(), React.INSTANCE.getScheduler());

        for (final AimModel model : React.INSTANCE.getAimModels().all()) {
            model.onReload(player, this);
        }

    }

    @Override
    public @NotNull DecayStrategy decayStrategy() {
        return this.decayStrategy;
    }

    public void resetSamples() {
        requestGeneration.incrementAndGet();
        requesting.values().forEach(flag -> flag.set(false));
    }

    /** aim-ai.yml buffer.sequence: how many frames a model that buffers frames keeps. */
    public int bufferSequence() {
        return sampleWindowSize;
    }

    /** aim-ai.yml buffer.step: new frames between two requests of such a model. */
    public int bufferStep() {
        return requestStep;
    }

    /** Called for every rotation; each model decides itself when it has a window to send. */
    public void onRotation(final @NotNull RotationData data) {
        if (!shouldCheck()) {
            return;
        }
        for (final AimModel model : React.INSTANCE.getAimModels().all()) {
            model.onRotation(player, data, this);
        }
    }

    /** Claims the model's single request slot; false when it is off or a request is in flight. */
    public boolean tryStart(final @NotNull AimModel model) {
        return shouldCheck() && inferenceAimProcessor.isModelEnabled(model.id())
                && requesting.get(model.id()).compareAndSet(false, true);
    }

    /** Gives the slot back when the model had nothing to send after all. */
    public void cancel(final @NotNull AimModel model) {
        requesting.get(model.id()).set(false);
    }

    /** Sends a window the model encodes; the token is null when the key goes in a header. */
    public void send(final @NotNull AimModel model, final @NotNull Function<@Nullable String, HttpRequest> encoder) {
        final InferenceAimProcessor processor = inferenceAimProcessor;
        final long generation = requestGeneration.get();
        final long clientEpoch = processor.requestEpoch();
        try {
            processor.check(player.getName(), model, encoder, result ->
                    React.INSTANCE.getScheduler().scheduleEntity(player.bukkitPlayer, () -> {
                        processor.publishIfCurrent(clientEpoch, () -> {
                            if (requestGeneration.get() == generation) onServerResult(model.id(), result);
                        });
                    }, TickSchedule.instant()));
        } catch (Throwable failure) {
            cancel(model);
            React.INSTANCE.getLogger().warn("Could not start {} request for {}", model.id().displayName(), player.getName(), failure);
        }
    }

    private void onServerResult(
            final @NotNull ModelId model,
            final @NotNull InferenceResult result
    ) {
        try {

            if (!player.bukkitPlayer.isOnline() || !shouldCheck()) {
                return;
            }

            if (!result.isAvailable()) {

                if (debug) {
                    debugHandler.debug("ML result unavailable");
                }

                return;
            }

            final double probability = result.getProbability();

            if (debug) {
                debugHandler.debug("Received ML response: " + probability);
            }

            final TextColor color = colorResolver.resolve(probability);

            final Component verbose = Component.text(
                    // Presentation only: keep the original double for decisions,
                    // events and persistence, including sub-micro probabilities.
                    ProbabilityFormatter.format(probability) + " (" + result.modelDisplay() + ")"
            ).color(color);

            final double threshold = Double.isFinite(result.getDecisionThreshold())
                    ? result.getDecisionThreshold() : alertThreshold;
            final boolean flagged = Double.isFinite(result.getDecisionThreshold())
                    ? probability >= threshold : probability > threshold;
            if (!result.isEnforcementEnabled()) {
                if (debug) debugHandler.debug("Observation (" + model.displayName() + "): " + probability + ", threshold=" + threshold);
            } else if (flagged) {

                if (debug) {
                    debugHandler.debug(
                            "Flagged: " + probability + " > " + alertThreshold
                    );
                }

                failAndAlert(1, verbose);

            } else {

                if (debug) {
                    debugHandler.debug(
                            "Reward: " + probability + " <= " + alertThreshold
                    );
                }

                reward();
            }

            verbose(verbose);

            final InferenceHistoryEntry entry = new InferenceHistoryEntry(this, probability);

            player.inferenceHistory.add(entry);

            new InferenceHistoryEntryAddedEvent(player, entry).callEvent();

        } finally {

            requesting.get(model).set(false);

            if (debug) {
                debugHandler.debug("Request finished");
            }
        }
    }

}
