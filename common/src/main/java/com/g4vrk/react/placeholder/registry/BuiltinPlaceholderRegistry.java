package com.g4vrk.react.placeholder.registry;

import com.g4vrk.react.api.check.Check;
import com.g4vrk.react.api.placeholder.registry.PlaceholderRegistry;
import com.g4vrk.react.color.ProbabilityColor;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.util.ProbabilityFormatter;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the placeholders the plugin itself offers.
 *
 * <p>{@code vl} gives the violation level of the check named by the query, or the sum over all
 * checks without a query. {@code avg-probability} gives the colored average probability, or the
 * plain number with the query {@code plain}. {@code history-size} gives the number of stored
 * inferences.</p>
 */
public final class BuiltinPlaceholderRegistry {

    private BuiltinPlaceholderRegistry() {

        throw new UnsupportedOperationException("Utility class");

    }

    /**
     * Adds the plugin placeholders to a registry.
     *
     * @param registry the registry to fill
     */
    public static void register(final @NotNull PlaceholderRegistry registry) {

        registry.register("vl", (player, query) -> {
            if (query.isEmpty()) {
                double totalViolations = 0.0D;
                for (final Check check : player.checks()) {
                    totalViolations += check.violations();
                }
                return number(totalViolations);
            }

            final Check check = player.check(query);
            return check == null ? null : number(check.violations());
        });

        registry.register("avg-probability", (player, query) -> {
            if (!(player instanceof TrackedPlayerImpl impl)) {
                return null;
            }

            final double average = impl.inferenceStatistic().averageProbability();
            return query.equals("plain")
                    ? ProbabilityFormatter.format(average)
                    : ProbabilityColor.miniMessage(average);
        });

        registry.register(
                "history-size",
                (player, query) -> player instanceof TrackedPlayerImpl impl
                        ? Integer.toString(impl.inferenceHistory().size())
                        : null
        );

    }

    private static @NotNull String number(
            final double value
    ) {

        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        if (value == Math.rint(value) && Math.abs(value) <= Long.MAX_VALUE) {
            return Long.toString((long) value);
        }
        final double scaledValue = value * 100.0D;
        if (Math.abs(scaledValue) <= Long.MAX_VALUE) {
            final long roundedHundredths = Math.round(scaledValue);
            final long whole = roundedHundredths / 100L;
            final long frac = Math.abs(roundedHundredths % 100L);
            if (frac == 0L) {
                return Long.toString(whole);
            }
            final StringBuilder builder = new StringBuilder(16);
            if (roundedHundredths < 0L && whole == 0L) {
                builder.append('-');
            }
            builder.append(whole).append('.');
            if (frac < 10L) {
                builder.append('0');
            }
            builder.append(frac);
            return builder.toString();
        }
        return Double.toString(value);

    }

}
