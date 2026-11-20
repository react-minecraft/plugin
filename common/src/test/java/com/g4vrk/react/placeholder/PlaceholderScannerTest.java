package com.g4vrk.react.placeholder;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers placeholder arguments, empty nested values and recursion limits.
 */
final class PlaceholderScannerTest {

    @Test
    void resolvesEachPlaceholderOnceWithItsActualQuery() {

        final AtomicInteger calls = new AtomicInteger();
        final String result = PlaceholderScanner.apply("%key% %key_value% {key} {key:value}", (key, query, bracket) -> {
            if (!key.equals("key")) {
                return null;
            }
            calls.incrementAndGet();
            return "[" + query + "]";
        });

        assertEquals("[] [value] [] [value]", result);
        assertEquals(4, calls.get());

    }

    @Test
    void preservesUnclosedOuterPlaceholderAfterEmptyNestedValue() {

        assertEquals("{outer:", PlaceholderScanner.apply("{outer:{empty}", (key, query, bracket) ->
                key.equals("empty") ? "" : null
        ));

    }

    @Test
    void resolvesNestedValuesIncludingEmptyOnes() {

        assertEquals("[abc] []", PlaceholderScanner.apply("%outer_%inner%% %outer_%empty%%", (key, query, bracket) ->
                switch (key) {
                    case "inner" -> "abc";
                    case "empty" -> "";
                    case "outer" -> "[" + query + "]";
                    default -> null;
                }
        ));

    }

    @Test
    void boundsNestedRecursion() {

        final String input = "{".repeat(20_000);
        assertEquals(input, PlaceholderScanner.apply(input, (key, query, bracket) -> null));

    }

}
