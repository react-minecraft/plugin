package com.g4vrk.react.history.entry;

import com.g4vrk.react.check.Check;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import static java.lang.System.currentTimeMillis;

@Getter
public final class InferenceHistoryEntry {

    private final long timestamp;

    private final Check check;

    private final double probability;

    public InferenceHistoryEntry(
            final @NotNull Check check,
            final double probability
    ) {
        this(currentTimeMillis(), check, probability);
    }

    public InferenceHistoryEntry(
            final long timestamp,
            final @NotNull Check check,
            final double probability
    ) {
        this.timestamp = timestamp;
        this.check = check;
        this.probability = probability;
    }

}
