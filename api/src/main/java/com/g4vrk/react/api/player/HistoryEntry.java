package com.g4vrk.react.api.player;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * One model verdict in a player's history.
 *
 * @param timestamp   when it was recorded, in epoch milliseconds
 * @param check       the id of the check that produced it
 * @param probability the cheating probability, from {@code 0} to {@code 1}
 */
@ApiStatus.AvailableSince("2.0")
public record HistoryEntry(long timestamp, @NotNull String check, double probability) {
}
