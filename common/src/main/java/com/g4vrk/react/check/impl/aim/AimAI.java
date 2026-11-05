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
import com.g4vrk.react.inference.ModelId;
import com.g4vrk.react.inference.aim.InferenceAimProcessor;
import com.g4vrk.react.inference.aim.InferenceResult;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.model.rotation.Rotation;
import com.g4vrk.react.player.model.rotation.RotationData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import com.g4vrk.react.vector.model.AimFrame;
import com.g4vrk.schedula.task.TickSchedule;
import java.util.concurrent.atomic.AtomicLong;
import com.g4vrk.react.inference.ProbabilityFormatter;

@CheckInfo(
        name = "Aim-AI",
        configId = "aim-ai"
)
public final class AimAI extends Check implements ReloadObserver {

    private static final List<ModelId> FLOWER_MODELS = Arrays.stream(ModelId.values()).filter(ModelId::isFlower).toList();

    private final ValueColorResolver colorResolver;

    private final DebugHandler debugHandler;

    private InferenceAimProcessor inferenceAimProcessor;

    private boolean debug;

    private int sampleWindowSize;
    private int requestStep;
    private double alertThreshold;

    // Each model has one request in flight at a time, independently of the others.
    private final Map<ModelId, AtomicBoolean> requesting = new EnumMap<>(ModelId.class);
    private final Map<ModelId, AtomicInteger> flowerSamples = new EnumMap<>(ModelId.class);
    private final AtomicLong requestGeneration = new AtomicLong();

    private DecayStrategy decayStrategy;

    public AimAI(@NotNull ReactPlayer player) {
        super(player);

        for (final ModelId model : ModelId.values()) {
            requesting.put(model, new AtomicBoolean());
            flowerSamples.put(model, new AtomicInteger());
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
        player.vectorData.resize(this.sampleWindowSize);
        this.alertThreshold = config.node("alert", "threshold").getDouble(0.49D);

        final double decayAmount = config.node("decay", "amount").getDouble(0.5D);

        this.decayStrategy = new LinearDecay(decayAmount);

        this.inferenceAimProcessor = new InferenceAimProcessor(React.INSTANCE.getLogger(),
                React.INSTANCE.getInferenceServer(), React.INSTANCE.getScheduler());

    }

    @Override
    public @NotNull DecayStrategy decayStrategy() {
        return this.decayStrategy;
    }

    public void resetSamples() {
        requestGeneration.incrementAndGet();
        requesting.values().forEach(flag -> flag.set(false));
        flowerSamples.values().forEach(samples -> samples.set(0));
    }

    public void onVectorFrame() {
        if (!player.vectorData.combatActivity.isActive() || !shouldCheck()
                || !inferenceAimProcessor.isModelEnabled(ModelId.VECTOR_PREVIEW)) {
            return;
        }
        player.vectorData.resize(sampleWindowSize);
        final AtomicBoolean inFlight = requesting.get(ModelId.VECTOR_PREVIEW);
        if (inFlight.get()) return;
        final AimFrame[] snapshot = player.vectorData.pollSnapshot(requestStep);
        if (snapshot == null || !inFlight.compareAndSet(false, true)) {
            return;
        }
        final InferenceAimProcessor processor = inferenceAimProcessor;
        send(ModelId.VECTOR_PREVIEW, processor,
                handler -> processor.check(player.getName(), snapshot, handler));
    }

    /** Called for every rotation; each enabled Flower model keeps its own request step. */
    public void onRotation(final @NotNull RotationData data) {
        if (!player.combatActivity.isActive() || !shouldCheck()) {
            return;
        }
        final InferenceAimProcessor processor = inferenceAimProcessor;
        for (final ModelId model : FLOWER_MODELS) {
            if (processor.isModelEnabled(model)) {
                sendFlowerWindow(model, data, processor);
            }
        }
    }

    private void sendFlowerWindow(final ModelId model, final RotationData data, final InferenceAimProcessor processor) {
        final AtomicInteger samples = flowerSamples.get(model);
        if (samples.incrementAndGet() < model.step() || data.historySize() < model.window()) {
            return;
        }
        final AtomicBoolean inFlight = requesting.get(model);
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        final Rotation[] history = data.snapshotHistory();
        if (history.length < model.window()) {
            inFlight.set(false);
            return;
        }
        samples.set(0);
        final Rotation[] snapshot = Arrays.copyOfRange(history, history.length - model.window(), history.length);
        send(model, processor, handler -> processor.check(player.getName(), snapshot, model, handler));
    }

    private void send(
            final ModelId model,
            final InferenceAimProcessor processor,
            final Consumer<Consumer<InferenceResult>> request
    ) {
        final long generation = requestGeneration.get();
        final long clientEpoch = processor.requestEpoch();
        try {
            request.accept(result ->
                    React.INSTANCE.getScheduler().scheduleEntity(player.bukkitPlayer, () -> {
                        processor.publishIfCurrent(clientEpoch, () -> {
                            if (requestGeneration.get() == generation) onServerResult(model, result);
                        });
                    }, TickSchedule.instant()));
        } catch (Throwable failure) {
            requesting.get(model).set(false);
            React.INSTANCE.getLogger().warn("Could not start {} request for {}", model.displayName(), player.getName(), failure);
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
                if (debug) debugHandler.debug("Vector observation: " + probability + ", threshold=" + threshold);
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
