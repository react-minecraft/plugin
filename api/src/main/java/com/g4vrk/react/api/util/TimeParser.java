package com.g4vrk.react.api.util;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the short durations used in configs: a whole number followed by
 * {@code ms}, {@code s}, {@code m}, {@code h} or {@code d}, for example {@code 250ms} or {@code 30d}.
 */
public final class TimeParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)(ms|[smhd])$");

    private TimeParser() {
    }

    public static @NotNull Duration parse(final @NotNull String input) {
        final Matcher matcher = PATTERN.matcher(input.trim().toLowerCase(Locale.ROOT));

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid time '" + input + "', expected e.g. 250ms, 10s, 5m, 1h or 30d");
        }

        final long value = Long.parseLong(matcher.group(1));
        final ChronoUnit unit = switch (matcher.group(2)) {
            case "ms" -> ChronoUnit.MILLIS;
            case "s" -> ChronoUnit.SECONDS;
            case "m" -> ChronoUnit.MINUTES;
            case "h" -> ChronoUnit.HOURS;
            case "d" -> ChronoUnit.DAYS;
            default -> throw new IllegalStateException("Unexpected unit: " + matcher.group(2));
        };

        return Duration.of(value, unit);
    }

    public static @NotNull String format(final @NotNull Duration duration) {
        final long millis = duration.toMillis();

        if (millis % ChronoUnit.DAYS.getDuration().toMillis() == 0 && millis != 0) {
            return duration.toDays() + "d";
        }
        if (millis % ChronoUnit.HOURS.getDuration().toMillis() == 0 && millis != 0) {
            return duration.toHours() + "h";
        }
        if (millis % ChronoUnit.MINUTES.getDuration().toMillis() == 0 && millis != 0) {
            return duration.toMinutes() + "m";
        }
        if (millis % ChronoUnit.SECONDS.getDuration().toMillis() == 0 && millis != 0) {
            return duration.toSeconds() + "s";
        }
        return millis + "ms";
    }

}
