package com.g4vrk.react.api.event.check;

import com.g4vrk.react.api.check.Check;
import com.g4vrk.react.api.event.CancellableEvent;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Posted before the actions of a punishment rule run. Cancel it to skip them.
 */
@ApiStatus.AvailableSince("2.0")
public final class CheckPunishEvent extends CancellableEvent {

    private final Check check;
    private final double violations;
    private final List<String> actions;

    /**
     * Creates the event.
     *
     * @param check      the check whose rule matched
     * @param violations the level that reached the rule
     * @param actions    the action lines about to run
     */
    public CheckPunishEvent(
            final @NotNull Check check,
            final double violations,
            final @NotNull List<String> actions
    ) {
        this.check = check;
        this.violations = violations;
        this.actions = List.copyOf(actions);
    }

    /**
     * Gives the check.
     *
     * @return the check
     */
    public @NotNull Check check() {
        return check;
    }

    /**
     * Gives the violation level.
     *
     * @return the violation level
     */
    public double violations() {
        return violations;
    }

    /**
     * Gives the unmodifiable action lines.
     *
     * @return the unmodifiable action lines
     */
    public @NotNull List<String> actions() {
        return actions;
    }

}
