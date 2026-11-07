package com.g4vrk.react.api.player;

import com.g4vrk.react.api.check.Check;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A player the plugin tracks, with its checks.
 *
 * <p>This is the addon side of {@link RCPPlayer}. All methods are thread-safe.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface TrackedPlayer extends RCPPlayer {

    /**
     * Lists the checks running for this player.
     *
     * @return an unmodifiable list, in a fixed order
     */
    @NotNull List<? extends Check> checks();

    /**
     * Finds a check by its config id.
     *
     * @param id the id, case is ignored
     * @return the check, or {@code null} when there is none with this id
     */
    @Nullable Check check(final @NotNull String id);

}
