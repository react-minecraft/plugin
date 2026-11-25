package com.g4vrk.react.check.impl.aim;

import com.g4vrk.react.config.model.AimAIConfig;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.channel.registry.BuiltinChannelRegistry;
import com.g4vrk.react.api.event.channel.MonitorEvent;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.check.decay.DecayStrategy;
import com.g4vrk.react.check.decay.impl.LinearDecay;
import com.g4vrk.react.check.decay.impl.NoDecay;
import com.g4vrk.react.check.info.CheckInfo;
import com.g4vrk.react.color.ProbabilityColor;
import com.g4vrk.react.event.SimpleEventDispatcher;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.text.MiniTextFormatter;
import com.g4vrk.react.util.ProbabilityFormatter;
import org.jetbrains.annotations.NotNull;

/**
 * The aim check. It runs no model itself: RCPs read the player's rotations and hand
 * their results back, and this check turns each result into alerts, violations and history.
 */
@CheckInfo(
        name = "Aim-AI",
        configId = "aim-ai"
)
public final class AimAI extends Check implements ReloadObserver<AimAIConfig> {

    enum ResultAction {

        OBSERVE,
        FLAG,
        REWARD

    }

    private volatile double alertThreshold;

    private volatile DecayStrategy decayStrategy = NoDecay.INSTANCE;

    public AimAI(final @NotNull TrackedPlayerImpl player) {

        super(player);

    }

    @Override
    public void onReload(final @NotNull AimAIConfig config) {

        this.alertThreshold = config.alert().threshold();
        this.decayStrategy = new LinearDecay(config.decay().amount());

        final RCPHost host = React.INSTANCE.rcpHost();
        if (host != null) {
            host.reload(player);
        }

    }

    @Override
    public @NotNull DecayStrategy decayStrategy() {

        return this.decayStrategy;

    }

    public void onRotation(final float @NotNull [] sample) {

        final RCPHost host = React.INSTANCE.rcpHost();
        if (host != null && player.dataReady() && !player.rotationBypassed(id())) {
            host.rotation(player, sample);
        }

    }

    public void onResult(
            final long generation,
            final @NotNull InferenceResult result
    ) {

        if (!player.platform().online() || !active() || !player.isInferenceCurrent(generation)) {
            return;
        }

        final double probability = result.probability();
        final double threshold = result.hasThreshold() ? result.threshold() : alertThreshold;
        final boolean flagged = result.hasThreshold() ? probability >= threshold : probability > threshold;
        final double violationsBefore = violations();
        final ResultAction action = resultAction(result, flagged);

        String details = null;
        if (action == ResultAction.FLAG) {
            if (debugEnabled()) {
                debug("Flagged (" + result.displayName() + "): " + probability + " > " + threshold);
            }
            details = formatDetails(probability, result.displayName());
            if (fail(1, details, generation) && player.isInferenceCurrent(generation)) {
                alert(details);
            }
        } else if (action == ResultAction.REWARD) {
            if (debugEnabled()) {
                debug("Reward (" + result.displayName() + "): " + probability + " <= " + threshold);
            }
            if (player.isInferenceCurrent(generation)) {
                reward();
            }
        } else if (debugEnabled()) {
            debug("Observed (" + result.displayName() + "): " + probability + " / " + threshold);
        }

        if (!player.isInferenceCurrent(generation)) {
            return;
        }

        final SimpleEventDispatcher events = React.INSTANCE.events();
        if (!BuiltinChannelRegistry.MONITOR.recipients().isEmpty() || events.hasListeners(MonitorEvent.class)) {
            if (details == null) {
                details = formatDetails(probability, result.displayName());
            }
            monitor(details);
        }

        final InferenceHistoryEntry entry = new InferenceHistoryEntry(
                this,
                probability,
                result.modelId(),
                result.runId(),
                result.hasThreshold() ? result.threshold() : null,
                result.enforcement(),
                violationsBefore,
                violations()
        );

        player.inferenceHistory().add(entry);

        if (events.hasListeners(InferenceResultEvent.class)) {
            events.post(new InferenceResultEvent(this, probability, entry.timestamp()));
        }

    }

    private static @NotNull String formatDetails(
            final double probability,
            final @NotNull String displayName
    ) {

        return "<color:" + ProbabilityColor.color(probability).asHexString() + ">"
                + ProbabilityFormatter.format(probability)
                + " (" + MiniTextFormatter.escape(displayName) + ")"
                + "</color>";

    }

    static @NotNull ResultAction resultAction(
            final @NotNull InferenceResult result,
            final boolean flagged
    ) {

        if (!result.enforcement()) {
            return ResultAction.OBSERVE;
        }
        return flagged ? ResultAction.FLAG : ResultAction.REWARD;

    }

}
