package com.g4vrk.react.check.impl.aim;

import com.g4vrk.react.config.model.AimAIConfig;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.check.decay.DecayStrategy;
import com.g4vrk.react.check.decay.impl.LinearDecay;
import com.g4vrk.react.check.info.CheckInfo;
import com.g4vrk.react.color.ProbabilityColor;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.rcp.RCPHost;
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

    private double alertThreshold;

    private DecayStrategy decayStrategy;

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

    public void onRotation(final @NotNull Rotation rotation) {
        final RCPHost host = React.INSTANCE.rcpHost();
        if (host != null && active()) {
            host.rotation(player, rotation);
        }
    }

    public void onResult(final @NotNull InferenceResult result) {

        if (!player.bukkitPlayer.isOnline() || !active()) {
            return;
        }

        final double probability = result.probability();

        final String details = "<color:" + ProbabilityColor.color(probability).asHexString() + ">"
                + ProbabilityFormatter.format(probability) + " (" + result.displayName() + ")" + "</color>";

        final double threshold = result.hasThreshold() ? result.threshold() : alertThreshold;
        final boolean flagged = result.hasThreshold() ? probability >= threshold : probability > threshold;

        if (!result.enforcement()) {
            debug("Observation (" + result.displayName() + "): " + probability + ", threshold " + threshold);
        } else if (flagged) {
            debug("Flagged (" + result.displayName() + "): " + probability + " > " + threshold);
            failAndAlert(1, details);
        } else {
            debug("Reward (" + result.displayName() + "): " + probability + " <= " + threshold);
            reward();
        }

        monitor(details);

        final InferenceHistoryEntry entry = new InferenceHistoryEntry(this, probability);

        player.inferenceHistory.add(entry);

        React.INSTANCE.events().post(new InferenceResultEvent(this, probability, entry.getTimestamp()));
    }

}
