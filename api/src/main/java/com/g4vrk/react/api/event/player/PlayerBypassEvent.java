package com.g4vrk.react.api.event.player;

import com.g4vrk.react.api.event.CancellableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Posted before a temporary bypass is given or taken away. Cancel it to keep the old state.
 */
@ApiStatus.AvailableSince("2.0")
public final class PlayerBypassEvent extends CancellableEvent {

    private final UUID uniqueId;
    private final String check;
    private final Instant until;
    private final boolean granted;

    /**
     * Creates the event.
     *
     * @param uniqueId the player
     * @param check    the check id, or {@code null} for every check
     * @param until    the end, or {@code null} for no end
     * @param granted  {@code true} when given, {@code false} when taken away
     */
    public PlayerBypassEvent(
            final @NotNull UUID uniqueId,
            final @Nullable String check,
            final @Nullable Instant until,
            final boolean granted
    ) {
        this.uniqueId = uniqueId;
        this.check = check;
        this.until = until;
        this.granted = granted;
    }

    /**
     * Gives the player unique id.
     *
     * @return the player unique id
     */
    public @NotNull UUID uniqueId() {
        return uniqueId;
    }

    /**
     * Gives the check id, or {@code null} for every check.
     *
     * @return the check id, or {@code null} for every check
     */
    public @Nullable String check() {
        return check;
    }

    /**
     * Gives the end, or {@code null} when it never ends.
     *
     * @return the end, or {@code null} when it never ends
     */
    public @Nullable Instant until() {
        return until;
    }

    /**
     * Gives whether the bypass is given.
     *
     * @return whether the bypass is given
     */
    public boolean granted() {
        return granted;
    }

}
