package com.g4vrk.react.check;

import com.g4vrk.react.React;
import com.g4vrk.react.api.channel.CheckReport;
import com.g4vrk.react.api.channel.BuiltinChannels;
import com.g4vrk.react.api.event.channel.AlertEvent;
import com.g4vrk.react.api.event.channel.MonitorEvent;
import com.g4vrk.react.api.event.check.CheckFlagEvent;
import com.g4vrk.react.check.decay.DecayStrategy;
import com.g4vrk.react.check.info.CheckInfo;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.punish.PunishmentManager;
import com.g4vrk.react.storage.ViolationLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/**
 * Common implementation of one player's configured check and violation state.
 */
@Accessors(fluent = true)
public abstract class Check implements com.g4vrk.react.api.check.Check {

    @Getter
    protected final TrackedPlayerImpl player;

    @Getter
    private final String name;
    @Getter
    private final String id;
    @Getter
    private final boolean experimental;

    private final ViolationLevel violationLevel;

    protected Check(
            final @NotNull TrackedPlayerImpl player
    ) {
        final CheckInfo info = this.getClass().getAnnotation(CheckInfo.class);

        if (info == null) {
            throw new IllegalStateException(
                    this.getClass().getName() + " is missing the @CheckInfo annotation"
            );
        }

        this.name = info.name();
        this.id = info.configId();
        this.experimental = info.experimental();

        this.player = player;
        this.violationLevel = new ViolationLevel(React.INSTANCE.storageManager(), player.uniqueId(), id);
    }

    @Override
    public final boolean active() {
        return player.dataReady() && !player.bypassed(id());
    }

    /**
     * Raises the violation level unless a {@link CheckFlagEvent} handler cancels it.
     *
     * @param amount how much to add
     * @return {@code true} when the level was raised
     */
    protected final boolean fail(
            final double amount
    ) {
        return this.fail(amount, "");
    }

    private boolean fail(
            final double amount,
            final @NotNull String details
    ) {
        if (!Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException("Violation amount must be finite and positive");
        }

        final CheckFlagEvent event = React.INSTANCE.events().post(new CheckFlagEvent(this, amount, details));

        if (event.cancelled()) {
            return false;
        }

        final double adjustedAmount = event.amount();
        if (!Double.isFinite(adjustedAmount) || adjustedAmount <= 0.0D) {
            throw new IllegalArgumentException("Adjusted violation amount must be finite and positive");
        }

        this.apply(adjustedAmount);
        return true;
    }

    private synchronized void apply(
            final double amount
    ) {
        final double previous = violationLevel.add(amount);
        final PunishmentManager punishmentManager = React.INSTANCE.punishmentManager();

        if (punishmentManager != null) {
            punishmentManager.handleFail(this, previous, previous + amount);
        }
    }

    protected final void failAndAlert(
            final double amount
    ) {
        this.failAndAlert(amount, "");
    }

    /**
     * Raises the violation level and publishes an alert.
     *
     * @param amount  how much to add
     * @param details shown in {@code <details>}, MiniMessage or {@code &} codes
     */
    protected final void failAndAlert(
            final double amount,
            final @NotNull String details
    ) {
        if (this.fail(amount, details)) {
            this.alert(details);
        }
    }

    protected final void failAndMonitor(
            final double amount,
            final @NotNull String details
    ) {
        if (this.fail(amount, details)) {
            this.monitor(details);
        }
    }

    protected final void alert() {
        this.alert("");
    }

    protected final void alert(
            final @NotNull String details
    ) {
        final AlertEvent event = React.INSTANCE.events().post(new AlertEvent(this.report(details)));

        if (!event.cancelled()) {
            BuiltinChannels.ALERTS.publish(event.report());
        }
    }

    protected final void monitor(
            final @NotNull String details
    ) {
        final MonitorEvent event = React.INSTANCE.events().post(new MonitorEvent(this.report(details)));

        if (!event.cancelled()) {
            BuiltinChannels.MONITOR.publish(event.report());
        }
    }

    @Override
    public final void debug(final @NotNull String message) {
        if (!BuiltinChannels.DEBUG.recipients().isEmpty()) {
            BuiltinChannels.DEBUG.publish(this.report(message));
        }
    }

    public final @NotNull CheckReport report(final @NotNull String details) {
        return new CheckReport(player.uniqueId(), player.name(), name(), id(), violations(), details);
    }

    protected final synchronized void reward() {

        violationLevel.decay(decayStrategy());

    }

    @Override
    public final double violations() {

        return violationLevel.value();

    }

    public final @NotNull ViolationLevel violationLevel() {

        return violationLevel;

    }

    @Override
    public final void addViolations(final double amount) {
        this.fail(amount);
    }

    @Override
    public final synchronized void resetViolations() {

        violationLevel.reset();

    }

    public abstract @NotNull DecayStrategy decayStrategy();

}
