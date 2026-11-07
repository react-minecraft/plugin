package com.g4vrk.react.api.check;

import com.g4vrk.react.api.player.TrackedPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * One check running for one player, together with its violation level.
 *
 * <p>Every player has its own instance of each check. All methods are thread-safe.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface Check {

    /**
     * Gives the id used in configs, permissions and commands.
     *
     * @return the lower-case id, for example {@code aim}
     */
    @NotNull String id();

    /**
     * Gives the name shown in alerts.
     *
     * @return the display name, for example {@code AimAI}
     */
    @NotNull String name();

    /**
     * Tells whether the check is still experimental.
     *
     * @return {@code true} for experimental checks
     */
    boolean experimental();

    /**
     * Gives the player this instance runs for.
     *
     * @return the owning player
     */
    @NotNull TrackedPlayer player();

    /**
     * Gives the current violation level.
     *
     * @return the violation level, never negative
     */
    double violations();

    /**
     * Raises the violation level without sending an alert, running punishments as a flag would.
     *
     * @param amount how much to add, must be positive
     */
    void addViolations(final double amount);

    /**
     * Drops the violation level to zero and stores it.
     */
    void resetViolations();

    /**
     * Tells whether the check runs for its player right now.
     *
     * @return {@code false} while the player has a bypass or the data is not loaded yet
     */
    boolean active();

}
