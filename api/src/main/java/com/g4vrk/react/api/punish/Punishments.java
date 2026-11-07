package com.g4vrk.react.api.punish;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * The punishment rules loaded from {@code punishments.yml}, read-only.
 *
 * <p>The rules are replaced on {@code /react reload}, so read them when needed instead of
 * keeping the lists. To stop or change a punishment, listen to {@code CheckPunishEvent}.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface Punishments {

    /**
     * Gives the ids of the checks that have rules.
     *
     * @return lowercased check ids, unmodifiable
     */
    @NotNull Set<String> checks();

    /**
     * Gives the rules of one check.
     *
     * @param check the check id, case is ignored
     * @return the rules, empty when the check has none
     */
    @NotNull List<PunishmentRule> rules(final @NotNull String check);

}
