package com.g4vrk.react.storage.config;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Connection pool sizing and timeout parameters shared by JDBC storage dialects.
 */
public record PoolConfig(
        int maximumPoolSize,
        int minimumIdle,
        @NotNull Duration connectionTimeout,
        @NotNull Duration validationTimeout,
        @NotNull Duration idleTimeout,
        @NotNull Duration maxLifetime
) {

}
