package com.g4vrk.react.check;

import com.g4vrk.react.Permissions;
import com.g4vrk.react.React;
import com.g4vrk.react.api.channel.CheckReport;
import com.g4vrk.react.api.channel.ReactChannels;
import com.g4vrk.react.api.event.PlayerCheckFailEvent;
import com.g4vrk.react.check.decay.DecayStrategy;
import com.g4vrk.react.check.info.CheckInfo;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.punish.PunishmentManager;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public abstract class Check extends AbstractCheck {

    @Getter
    protected final ReactPlayer player;

    @Getter
    private volatile double violations;

    protected Check(
            @NotNull ReactPlayer player
    ) {
        super();

        final CheckInfo info = this.getClass().getAnnotation(CheckInfo.class);

        if (info == null) {
            throw new IllegalStateException(
                    this.getClass().getName() + " is missing the @CheckInfo annotation"
            );
        }

        super.setName(info.name());
        super.setConfigId(info.configId());
        super.setExperimental(info.experimental());

        this.player = player;
    }

    public final boolean shouldCheck() {
        return player.isDataReady() && !Permissions.hasBypassForCheck(
                player.bukkitPlayer,
                getConfigId()
        );
    }

    protected final synchronized void fail(
            final double amount
    ) {
        final double previous = this.violations;

        this.violations += amount;
        this.persistViolations();

        final PunishmentManager punishmentManager = React.INSTANCE.punishmentManager();

        if (punishmentManager != null) {
            punishmentManager.handleFail(this, previous, this.violations);
        }

        new PlayerCheckFailEvent(player, this).callEvent();

    }

    protected final void failAndAlert(
            final double amount
    ) {
        this.failAndAlert(amount, "");
    }

    /**
     * @param details shown in {@code {details}}, MiniMessage or {@code &} codes
     */
    protected final void failAndAlert(
            final double amount,
            final @NotNull String details
    ) {
        this.fail(amount);
        this.alert(details);
    }

    protected final void failAndMonitor(
            final double amount,
            final @NotNull String details
    ) {
        this.fail(amount);
        this.monitor(details);
    }

    protected final void alert() {
        this.alert("");
    }

    protected final void alert(
            final @NotNull String details
    ) {
        ReactChannels.ALERTS.publish(this.report(details));
    }

    protected final void monitor(
            final @NotNull String details
    ) {
        ReactChannels.MONITOR.publish(this.report(details));
    }

    public final @NotNull CheckReport report(final @NotNull String details) {
        return new CheckReport(player.getUniqueId(), player.getName(), getName(), violations, details);
    }

    protected final synchronized void reward() {
        final double updated = Math.max(0, decayStrategy().decay(violations));
        if (Double.compare(updated, violations) == 0) {
            return;
        }
        violations = updated;
        this.persistViolations();
    }

    public final synchronized void mergeViolations(
            final double storedViolations,
            final long storedUpdatedAt,
            final long sessionStartedAt
    ) {
        if (storedUpdatedAt >= sessionStartedAt) {
            return;
        }
        this.violations += storedViolations;
    }

    public final synchronized void resetViolations() {
        violations = 0D;
        this.persistViolations();
    }

    public final synchronized void restoreViolations(final double violations) {
        this.violations = Math.max(0.0D, violations);
    }

    private void persistViolations() {
        if (React.INSTANCE.storageManager() != null) {
            React.INSTANCE.storageManager().saveViolation(
                    player.getUniqueId(),
                    getConfigId(),
                    violations
            );
        }
    }

    public abstract @NotNull DecayStrategy decayStrategy();

}
