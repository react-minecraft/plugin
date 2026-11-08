package com.g4vrk.react.api.punish.registry;

import com.g4vrk.react.api.punish.PunishmentRule;
import com.g4vrk.react.api.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * The punishment rules loaded from {@code punishments.yml}, by check id, read-only.
 *
 * <p>The rules are replaced on {@code /react reload}, so read them when needed instead of
 * keeping the lists. To stop or change a punishment, listen to {@code CheckPunishEvent}.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface PunishmentRegistry extends Registry<String, List<PunishmentRule>> {

    /**
     * Gives the rules of one check.
     *
     * @param check the check id, case is ignored
     * @return the rules, empty when the check has none
     */
    default @NotNull List<PunishmentRule> rules(final @NotNull String check) {
        final List<PunishmentRule> rules = this.get(check.toLowerCase(Locale.ROOT));
        return rules == null ? List.of() : rules;
    }

}
