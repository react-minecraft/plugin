package com.g4vrk.react.storage.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Immutable runtime configuration for database persistence, pooling, batching, and retry backoff.
 */
public record DatabaseConfig(
        boolean enabled,
        @NotNull DatabaseType type,
        @NotNull Duration historyRetention,
        @NotNull Duration cleanupInterval,
        int executorThreads,
        int executorQueueCapacity,
        int pendingWriteCapacity,
        int batchSize,
        @NotNull Duration flushInterval,
        @NotNull Duration retryInitialDelay,
        @NotNull Duration retryMaximumDelay,
        double retryMultiplier,
        @NotNull Duration shutdownTimeout,
        @Nullable JdbcConfig jdbc,
        @Nullable MongoConfig mongo
) {

}
