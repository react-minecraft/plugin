package com.g4vrk.react.placeholder;

import com.g4vrk.react.api.check.Check;
import com.g4vrk.react.api.placeholder.PlaceholderRegistry;
import com.g4vrk.react.color.ProbabilityColor;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.util.ProbabilityFormatter;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Registers the placeholders the plugin itself offers.
 *
 * <p>{@code vl} gives the violation level of the check named by the query, or the sum over all
 * checks without a query. {@code avg-probability} gives the colored average probability, or the
 * plain number with the query {@code plain}. {@code history-size} gives the number of stored
 * inferences.</p>
 */
public final class BuiltinPlaceholders {

    private BuiltinPlaceholders() {
    }

    /**
     * Adds the plugin placeholders to a registry.
     *
     * @param registry the registry to fill
     */
    public static void register(final @NotNull PlaceholderRegistry registry) {
        registry.register("vl", (player, query) -> {
            if (query.isEmpty()) {
                return number(player.checks().stream().mapToDouble(Check::violations).sum());
            }

            final Check check = player.check(query);
            return check == null ? null : number(check.violations());
        });

        registry.register("avg-probability", (player, query) -> {
            if (!(player instanceof TrackedPlayerImpl impl)) {
                return null;
            }

            final double average = impl.inferenceStatistic.averageProbability();
            return query.equals("plain")
                    ? ProbabilityFormatter.format(average)
                    : ProbabilityColor.miniMessage(average);
        });

        registry.register("history-size", (player, query) -> player instanceof TrackedPlayerImpl impl
                ? Integer.toString(impl.inferenceHistory.size())
                : null);
    }

    private static @NotNull String number(final double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

}
