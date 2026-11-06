package com.g4vrk.react.player;

import com.g4vrk.react.React;
import com.g4vrk.react.api.channel.print.impl.AlertPrinter;
import com.g4vrk.react.api.channel.print.impl.VerbosePrinter;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.check.manager.CheckManager;
import com.g4vrk.react.history.InferenceHistory;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.statistic.InferenceStatistic;
import com.g4vrk.react.storage.model.PlayerStorageData;
import com.g4vrk.react.storage.model.StoredInference;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.g4vrk.react.player.model.rotation.RotationData;
import com.g4vrk.react.check.impl.aim.AimAI;

public final class ReactPlayer implements RCPPlayer {

    private static final int MAX_ROTATION_WINDOW = 40;

    @Getter
    private final UUID uniqueId;
    @Getter
    private final String name;

    public final Player bukkitPlayer;

    public final CombatActivity combatActivity;
    public final RotationData rotationData = new RotationData(MAX_ROTATION_WINDOW);

    public final AlertPrinter alertPrinter;
    public final VerbosePrinter verbosePrinter;
    public final CheckManager checkManager;

    public final InferenceHistory inferenceHistory;
    public final InferenceStatistic inferenceStatistic;

    private final AtomicBoolean dataReady = new AtomicBoolean();
    private final long sessionStartedAt = System.currentTimeMillis();

    public ReactPlayer(
            @NotNull UUID uniqueId,
            @NotNull String name,
            @NotNull Player bukkitPlayer
    ) {
        this.uniqueId = uniqueId;
        this.name = name;
        this.bukkitPlayer = bukkitPlayer;
        this.alertPrinter = React.INSTANCE.getAlertPrinter();
        this.verbosePrinter = React.INSTANCE.getVerbosePrinter();
        this.checkManager = new CheckManager(this);
        this.inferenceHistory = new InferenceHistory(
                React.INSTANCE.getStorageManager().historyRetention(),
                entry -> React.INSTANCE.getStorageManager().saveInference(
                        uniqueId,
                        new StoredInference(
                                entry.getTimestamp(),
                                entry.getCheck().getConfigId(),
                                entry.getProbability()
                        )
                )
        );
        this.inferenceStatistic = new InferenceStatistic(this.inferenceHistory);

        this.combatActivity = new CombatActivity();
    }

    @Override
    public @NotNull UUID uniqueId() {
        return uniqueId;
    }

    @Override
    public @NotNull String name() {
        return name;
    }

    @Override
    public @NotNull Player bukkit() {
        return bukkitPlayer;
    }

    @Override
    public boolean inCombat() {
        return combatActivity.isActive();
    }

    @Override
    public void enterCombat() {
        React.INSTANCE.getCombatListener().activate(this);
    }

    @Override
    public boolean checked() {
        final AimAI aim = checkManager.getCheck(AimAI.class);
        return aim != null && aim.shouldCheck();
    }

    @Override
    public @NotNull Rotation @NotNull [] rotations() {
        return rotationData.snapshotHistory();
    }

    public void clearTransientState() {
        rotationData.clear();
        combatActivity.reset();
        if (React.INSTANCE.getRcpHost() != null) {
            React.INSTANCE.getRcpHost().reset(uniqueId);
        }
    }

    public boolean isDataReady() {
        return dataReady.get();
    }

    public void markDataReady() {
        dataReady.set(true);
    }

    public void applyStorageData(final @NotNull PlayerStorageData data) {
        applyViolations(data, false);
        inferenceHistory.replace(mapHistory(data));
    }

    public void mergeStorageData(final @NotNull PlayerStorageData data) {
        applyViolations(data, true);
        inferenceHistory.merge(mapHistory(data));
    }

    private void applyViolations(final @NotNull PlayerStorageData data, final boolean merge) {
        data.violations().forEach((configId, stored) -> {
            final Check check = checkManager.getCheck(configId);
            if (check != null) {
                final double violations;
                if (!merge) {
                    violations = stored.value();
                } else if (stored.updatedAt() >= sessionStartedAt) {
                    return;
                } else {
                    check.mergeViolations(
                            stored.value(),
                            stored.updatedAt(),
                            sessionStartedAt
                    );
                    return;
                }
                check.restoreViolations(violations);
            }
        });
    }

    private @NotNull List<InferenceHistoryEntry> mapHistory(final @NotNull PlayerStorageData data) {
        final List<InferenceHistoryEntry> loadedHistory = new ArrayList<>(data.inferenceHistory().size());
        for (final StoredInference stored : data.inferenceHistory()) {
            final Check check = checkManager.getCheck(stored.check());
            if (check != null) {
                loadedHistory.add(new InferenceHistoryEntry(
                        stored.timestamp(),
                        check,
                        stored.probability()
                ));
            }
        }
        return loadedHistory;
    }

}
