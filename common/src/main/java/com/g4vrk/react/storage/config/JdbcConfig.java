package com.g4vrk.react.storage.config;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * JDBC connection URL, driver credentials, and Hikari pool parameters.
 */
public record JdbcConfig(
        @NotNull String jdbcUrl,
        @NotNull String driverClassName,
        @NotNull String username,
        @NotNull String password,
        @NotNull PoolConfig pool,
        @NotNull Map<String, String> properties
) {

}
