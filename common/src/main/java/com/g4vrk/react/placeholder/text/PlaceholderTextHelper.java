package com.g4vrk.react.placeholder.text;

import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import org.jetbrains.annotations.NotNull;

/**
 * Small helpers for the placeholder paths, kept together so neither allocates more than it has to.
 */
public final class PlaceholderTextHelper {

    private static final String SEPARATOR = ":";

    private PlaceholderTextHelper() {

    }

    /**
     * Tells whether a text may hold a placeholder at all, without scanning it twice.
     *
     * @param text the text
     * @return {@code true} when the text holds {@code %} or {@code {}
     */
    public static boolean mentions(final @NotNull String text) {

        return text.indexOf('%') != -1 || text.indexOf('{') != -1;

    }

    /**
     * Joins the arguments of a MiniMessage tag with {@code :}.
     *
     * @param arguments the arguments of the tag
     * @return the query, empty when the tag has no arguments
     */
    public static @NotNull String join(final @NotNull ArgumentQueue arguments) {

        if (!arguments.hasNext()) {
            return "";
        }

        String query = arguments.pop().value();

        if (!arguments.hasNext()) {
            return query;
        }

        final StringBuilder builder = new StringBuilder(query.length() + 16).append(query);

        do {
            builder.append(SEPARATOR).append(arguments.pop().value());
        } while (arguments.hasNext());

        return builder.toString();

    }

}
