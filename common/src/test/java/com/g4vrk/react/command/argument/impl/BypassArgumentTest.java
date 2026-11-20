package com.g4vrk.react.command.argument.impl;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BypassArgumentTest {

    @Test
    void rejectsInvalidAndUnrepresentableExpirations() {

        final Instant now = Instant.EPOCH;
        assertEquals(now.plusSeconds(60), BypassArgument.expiration(now, Duration.ofMinutes(1)));
        assertNull(BypassArgument.expiration(now, Duration.ZERO));
        assertNull(BypassArgument.expiration(now, Duration.ofSeconds(-1)));
        assertNull(BypassArgument.expiration(now, Duration.ofSeconds(Long.MAX_VALUE)));
        assertNull(BypassArgument.expiration(now, Duration.ofMillis(Long.MAX_VALUE)));

    }

}
