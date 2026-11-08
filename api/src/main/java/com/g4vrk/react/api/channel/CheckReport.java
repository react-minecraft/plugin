package com.g4vrk.react.api.channel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * One result of a check, sent to {@link BuiltinChannels#ALERTS} when the player is flagged and to
 * {@link BuiltinChannels#MONITOR} on every analysis. Debug lines go to {@link BuiltinChannels#DEBUG}.
 *
 * @param player     the checked player
 * @param playerName the player's name at the time of the check
 * @param check      the check name, e.g. {@code Aim-AI}
 * @param checkId    the check's config id, e.g. {@code aim-ai}
 * @param violations the player's violations in this check after the result
 * @param details    extra text from the check, MiniMessage or {@code &} codes, may be empty
 */
@ApiStatus.AvailableSince("2.0")
public record CheckReport(
        @NotNull UUID player,
        @NotNull String playerName,
        @NotNull String check,
        @NotNull String checkId,
        double violations,
        @NotNull String details
) {
}
