package com.g4vrk.react.placeholder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Replaces placeholders in one pass over a character buffer.
 *
 * <p>The scanning algorithm is taken from <a href="https://github.com/by1337/BPlaceholder">
 * by1337/BPlaceholder</a>, {@code dev.by1337.plc.PlaceholderProcessor}, and adapted so it
 * resolves through the plugin's own {@link Lookup} instead of a placeholder set. It handles
 * {@code %key%}, {@code %key_query%}, <code>{key}</code> and <code>{key:query}</code>, nested
 * placeholders such as {@code %key_%other%%}, and stops after {@value #MAX_DEPTH} passes over
 * one spot so a placeholder that expands to itself cannot hang the server thread.</p>
 *
 * <p>Unlike a regex pass this allocates nothing but the buffer it writes into and the value it
 * puts there.</p>
 */
public final class PlaceholderScanner {

    private static final int MAX_DEPTH = 32;

    private static final char PAPI = '%';
    private static final char PAPI_PARAMS = '_';
    private static final char BRACKET = '{';
    private static final char BRACKET_END = '}';
    private static final char BRACKET_PARAMS = ':';
    private static final char SPACE = ' ';

    /**
     * Gives the value of one placeholder.
     */
    @FunctionalInterface
    public interface Lookup {

        /**
         * Resolves one placeholder.
         *
         * @param key     the key before the parameter separator
         * @param query   what follows the separator, empty when there is none
         * @param bracket {@code true} for a {@code {key:query}} placeholder, {@code false} for
         *                {@code %key_query%}
         * @return the value, or {@code null} to leave the placeholder as it is
         */
        @Nullable String resolve(final @NotNull String key, final @NotNull String query, final boolean bracket);

    }

    private final Buffer buffer;
    private final Lookup lookup;

    private PlaceholderScanner(final @NotNull String source, final @NotNull Lookup lookup) {
        this.buffer = new Buffer(source);
        this.lookup = lookup;
    }

    /**
     * Replaces every placeholder of a text.
     *
     * @param source the text
     * @param lookup the value source
     * @return the text with the known placeholders replaced
     */
    public static @NotNull String apply(final @NotNull String source, final @NotNull Lookup lookup) {
        if (source.isEmpty()) {
            return source;
        }

        final PlaceholderScanner scanner = new PlaceholderScanner(source, lookup);

        scanner.scan();

        return scanner.buffer.toString();
    }

    private void scan() {
        int index = 0;
        int depth = 0;

        while (index < this.buffer.size) {
            final char current = this.buffer.chars[index];

            if (current != PAPI && current != BRACKET) {
                index++;
                continue;
            }

            if (!this.resolveAt(index)) {
                index++;
                continue;
            }

            if (depth++ < MAX_DEPTH) {
                continue;
            }

            index++;
            depth = 0;
        }
    }

    private boolean resolveAt(final int offset) {
        final int start = offset;
        int index = offset + 1;

        final char open = this.buffer.chars[offset];
        final char closeChar;
        final char separator;
        final boolean bracket;

        if (open == PAPI) {
            closeChar = PAPI;
            separator = PAPI_PARAMS;
            bracket = false;
        } else if (open == BRACKET) {
            closeChar = BRACKET_END;
            separator = BRACKET_PARAMS;
            bracket = true;
        } else {
            return false;
        }

        final StringBuilder content = new StringBuilder(16);

        boolean found = false;
        String key = null;

        while (index < this.buffer.size) {
            char current = this.buffer.chars[index++];

            if (current == BRACKET || current == PAPI) {
                if (this.resolveAt(index - 1)) {
                    current = this.buffer.chars[index - 1];
                }
            }

            if (current == closeChar) {
                if (!found) {
                    key = content.toString();

                    if (this.lookup.resolve(key, "", bracket) == null) {
                        return false;
                    }
                }

                final String value = this.lookup.resolve(key, content.toString(), bracket);

                if (value == null) {
                    return false;
                }

                this.buffer.replace(start, index - start, value);

                return true;
            }

            if (current == separator && !found) {
                key = content.toString();
                found = this.lookup.resolve(key, "", bracket) != null;

                if (!found) {
                    return false;
                }

                content.setLength(0);
                continue;
            }

            if (!found && current == SPACE) {
                return false;
            }

            content.append(current);
        }

        return false;
    }

    /**
     * A growable character buffer that replaces slices in place, as BPlaceholder's does.
     */
    private static final class Buffer {

        private char[] chars;
        private int size;

        private Buffer(final @NotNull String source) {
            this.size = source.length();
            this.chars = new char[this.size + 15];
            source.getChars(0, this.size, this.chars, 0);
        }

        private void replace(final int start, final int length, final @NotNull String value) {
            final int valueLength = value.length();
            final int newSize = this.size - length + valueLength;

            if (newSize > this.chars.length) {
                this.chars = Arrays.copyOf(this.chars, Math.max(this.chars.length + 10, newSize));
            }

            final int tailStart = start + length;
            final int tailTarget = start + valueLength;
            final int tailLength = this.size - tailStart;

            if (tailLength > 0 && tailStart != tailTarget) {
                System.arraycopy(this.chars, tailStart, this.chars, tailTarget, tailLength);
            }

            if (valueLength != 0) {
                value.getChars(0, valueLength, this.chars, start);
            }

            this.size = newSize;
        }

        @Override
        public String toString() {
            return new String(this.chars, 0, this.size);
        }

    }

}
