package com.g4vrk.react.api.event.check;

import com.g4vrk.react.api.check.Check;
import com.g4vrk.react.api.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Posted before a check raises a violation level. Cancel it to drop the flag together with its alert.
 */
@ApiStatus.AvailableSince("2.0")
public final class CheckFlagEvent extends CancellableEvent {

    private final Check check;
    private volatile double amount;
    private final String details;

    /**
     * Creates the event.
     *
     * @param check   the check that flags
     * @param amount  how much the level will rise
     * @param details details shown in the alert, may be empty
     */
    public CheckFlagEvent(
            final @NotNull Check check,
            final double amount,
            final @NotNull String details
    ) {
        this.check = check;
        this.amount = amount;
        this.details = details;
    }

    /**
     * Gives the flagging check.
     *
     * @return the flagging check
     */
    public @NotNull Check check() {
        return check;
    }

    /**
     * Gives the amount added to the level.
     *
     * @return the amount added to the level
     */
    public double amount() {
        return amount;
    }

    /**
     * Changes the amount added to the level.
     *
     * @param amount the new value
     */
    public void amount(final double amount) {
        this.amount = amount;
    }

    /**
     * Gives the alert details.
     *
     * @return the alert details
     */
    public @NotNull String details() {
        return details;
    }

}
