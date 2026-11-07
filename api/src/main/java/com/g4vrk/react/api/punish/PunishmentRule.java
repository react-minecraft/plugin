package com.g4vrk.react.api.punish;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One rule of {@code punishments.yml}.
 *
 * <p>A rule from {@code at} runs once at {@code start} and has a {@code period} of {@code 0}.
 * A rule from {@code every} runs at {@code start}, then each {@code period} violations.</p>
 *
 * @param start  the violation level of the first run
 * @param period the step between runs, {@code 0} for a one-shot rule
 * @param lines  the action lines as written in the config
 */
@ApiStatus.AvailableSince("2.0")
public record PunishmentRule(int start, int period, @NotNull List<String> lines) {

    /**
     * Tells whether the rule runs more than once.
     *
     * @return {@code true} for a rule from {@code every}
     */
    public boolean repeating() {
        return period > 0;
    }

}
