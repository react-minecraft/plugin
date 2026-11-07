package com.g4vrk.react.api.player;

import com.g4vrk.react.api.check.Check;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    /**
     * Tells whether a check skips this player, by a temporary bypass or by the
     * {@code react.bypass.<check>} permission.
     *
     * @param check the check id, case is ignored
     * @return {@code true} when the check skips this player
     */
    boolean bypassed(final @NotNull String check);

    /**
     * Lists the temporary bypasses that have not ended.
     *
     * <p>The key is a check id, or {@code *} for every check. The value is when the bypass
     * ends, or {@code null} when it never does.</p>
     *
     * @return an unmodifiable snapshot
     */
    @NotNull Map<String, Instant> bypasses();

    /**
     * Grants a temporary bypass and stores it. Posts a cancellable {@code PlayerBypassEvent} first.
     *
     * @param check the check id, or {@code null} for every check
     * @param until when it ends, or {@code null} for never
     * @return {@code false} when a handler cancelled it
     */
    boolean bypass(final @Nullable String check, final @Nullable Instant until);

    /**
     * Lifts a temporary bypass. Posts a cancellable {@code PlayerBypassEvent} first.
     *
     * <p>A permission bypass stays.</p>
     *
     * @param check the check id, or {@code null} for the bypass of every check
     * @return {@code false} when there was no such bypass or a handler cancelled it
     */
    boolean removeBypass(final @Nullable String check);

    /**
     * Gives the model verdicts kept for this player, oldest first.
     *
     * <p>Entries older than the {@code history-retention} storage setting are left out.</p>
     *
     * @return an unmodifiable snapshot
     */
    @NotNull List<HistoryEntry> history();

}
