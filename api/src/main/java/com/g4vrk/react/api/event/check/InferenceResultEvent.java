package com.g4vrk.react.api.event.check;

import com.g4vrk.react.api.check.Check;
import com.g4vrk.react.api.event.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Posted when a model result for a player lands in the history.
 */
@ApiStatus.AvailableSince("2.0")
public final class InferenceResultEvent extends Event {

    private final Check check;
    private final double probability;
    private final long timestamp;

    /**
     * Creates the event.
     *
     * @param check       the check the result belongs to
     * @param probability the cheat probability from 0 to 1
     * @param timestamp   when the result came, in epoch milliseconds
     */
    public InferenceResultEvent(
            final @NotNull Check check,
            final double probability,
            final long timestamp
    ) {
        this.check = check;
        this.probability = probability;
        this.timestamp = timestamp;
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
     * Gives the probability.
     *
     * @return the probability
     */
    public double probability() {
        return probability;
    }

    /**
     * Gives the epoch milliseconds.
     *
     * @return the epoch milliseconds
     */
    public long timestamp() {
        return timestamp;
    }

}
