package com.g4vrk.react.storage.backend.jdbc;

import com.g4vrk.react.storage.config.DatabaseType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.g4vrk.react.storage.constant.StorageConstant.BYPASS_TABLE;
import static com.g4vrk.react.storage.constant.StorageConstant.INFERENCE_TABLE;
import static com.g4vrk.react.storage.constant.StorageConstant.VIOLATIONS_TABLE;

/**
 * SQL dialect definitions for SQLite, H2, and MySQL schemas and idempotent upserts.
 */
public enum JdbcDialect {
    SQLITE(
            List.of(
                    "CREATE TABLE IF NOT EXISTS " + VIOLATIONS_TABLE + " (player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, violations REAL NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, check_name))",
                    "CREATE TABLE IF NOT EXISTS " + INFERENCE_TABLE + " (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, recorded_at BIGINT NOT NULL, probability REAL NOT NULL, model_id VARCHAR(128), run_id VARCHAR(1024), decision_threshold DOUBLE, enforcement_enabled BOOLEAN, violations_before DOUBLE, violations_after DOUBLE)",
                    "CREATE INDEX IF NOT EXISTS react_inference_scores_player_time_idx ON " + INFERENCE_TABLE + " (player_uuid, recorded_at)",
                    "CREATE INDEX IF NOT EXISTS react_inference_scores_time_idx ON " + INFERENCE_TABLE + " (recorded_at)",
                    "CREATE TABLE IF NOT EXISTS " + BYPASS_TABLE + " (player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, expires_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, check_name))"
            ),
            "INSERT INTO " + VIOLATIONS_TABLE + " (player_uuid, check_name, violations, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT(player_uuid, check_name) DO UPDATE SET violations = excluded.violations, updated_at = excluded.updated_at WHERE excluded.updated_at >= " + VIOLATIONS_TABLE + ".updated_at",
            "INSERT OR IGNORE INTO " + INFERENCE_TABLE + " (id, player_uuid, check_name, recorded_at, probability, model_id, run_id, decision_threshold, enforcement_enabled, violations_before, violations_after) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "INSERT OR REPLACE INTO " + BYPASS_TABLE + " (player_uuid, check_name, expires_at) VALUES (?, ?, ?)"
    ),
    H2(
            List.of(
                    "CREATE TABLE IF NOT EXISTS " + VIOLATIONS_TABLE + " (player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, violations DOUBLE PRECISION NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, check_name))",
                    "CREATE TABLE IF NOT EXISTS " + INFERENCE_TABLE + " (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, recorded_at BIGINT NOT NULL, probability DOUBLE PRECISION NOT NULL, model_id VARCHAR(128), run_id VARCHAR(1024), decision_threshold DOUBLE PRECISION, enforcement_enabled BOOLEAN, violations_before DOUBLE PRECISION, violations_after DOUBLE PRECISION)",
                    "CREATE INDEX IF NOT EXISTS react_inference_scores_player_time_idx ON " + INFERENCE_TABLE + " (player_uuid, recorded_at)",
                    "CREATE INDEX IF NOT EXISTS react_inference_scores_time_idx ON " + INFERENCE_TABLE + " (recorded_at)",
                    "CREATE TABLE IF NOT EXISTS " + BYPASS_TABLE + " (player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, expires_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, check_name))"
            ),
            "MERGE INTO " + VIOLATIONS_TABLE + " target USING (VALUES (CAST(? AS VARCHAR(36)), CAST(? AS VARCHAR(64)), CAST(? AS DOUBLE PRECISION), CAST(? AS BIGINT))) source(player_uuid, check_name, violations, updated_at) ON (target.player_uuid = source.player_uuid AND target.check_name = source.check_name) WHEN MATCHED AND source.updated_at >= target.updated_at THEN UPDATE SET target.violations = source.violations, target.updated_at = source.updated_at WHEN NOT MATCHED THEN INSERT (player_uuid, check_name, violations, updated_at) VALUES (source.player_uuid, source.check_name, source.violations, source.updated_at)",
            "MERGE INTO " + INFERENCE_TABLE + " (id, player_uuid, check_name, recorded_at, probability, model_id, run_id, decision_threshold, enforcement_enabled, violations_before, violations_after) KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "MERGE INTO " + BYPASS_TABLE + " (player_uuid, check_name, expires_at) KEY (player_uuid, check_name) VALUES (?, ?, ?)"
    ),
    MYSQL(
            List.of(
                    "CREATE TABLE IF NOT EXISTS " + VIOLATIONS_TABLE + " (player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, violations DOUBLE NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, check_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                    "CREATE TABLE IF NOT EXISTS " + INFERENCE_TABLE + " (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, recorded_at BIGINT NOT NULL, probability DOUBLE NOT NULL, model_id VARCHAR(128), run_id VARCHAR(1024), decision_threshold DOUBLE, enforcement_enabled BOOLEAN, violations_before DOUBLE, violations_after DOUBLE, INDEX react_inference_scores_player_time_idx (player_uuid, recorded_at), INDEX react_inference_scores_time_idx (recorded_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                    "CREATE TABLE IF NOT EXISTS " + BYPASS_TABLE + " (player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, expires_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, check_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            ),
            "INSERT INTO " + VIOLATIONS_TABLE + " (player_uuid, check_name, violations, updated_at) VALUES (?, ?, ?, ?) AS incoming ON DUPLICATE KEY UPDATE violations = IF(incoming.updated_at >= " + VIOLATIONS_TABLE + ".updated_at, incoming.violations, " + VIOLATIONS_TABLE + ".violations), updated_at = GREATEST(" + VIOLATIONS_TABLE + ".updated_at, incoming.updated_at)",
            "INSERT INTO " + INFERENCE_TABLE + " (id, player_uuid, check_name, recorded_at, probability, model_id, run_id, decision_threshold, enforcement_enabled, violations_before, violations_after) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS incoming ON DUPLICATE KEY UPDATE id = " + INFERENCE_TABLE + ".id",
            "REPLACE INTO " + BYPASS_TABLE + " (player_uuid, check_name, expires_at) VALUES (?, ?, ?)"
    );

    private final List<String> schemaStatements;
    private final String violationUpsert;
    private final String inferenceInsert;
    private final String bypassUpsert;

    JdbcDialect(
            final @NotNull List<String> schemaStatements,
            final @NotNull String violationUpsert,
            final @NotNull String inferenceInsert,
            final @NotNull String bypassUpsert
    ) {

        this.schemaStatements = schemaStatements;
        this.violationUpsert = violationUpsert;
        this.inferenceInsert = inferenceInsert;
        this.bypassUpsert = bypassUpsert;

    }

    public static @NotNull JdbcDialect forType(final @NotNull DatabaseType type) {

        return switch (type) {
            case SQLITE -> SQLITE;
            case H2 -> H2;
            case MYSQL -> MYSQL;
            case MONGODB -> throw new IllegalArgumentException("MongoDB has no JDBC dialect");
        };

    }

    public @NotNull List<String> schemaStatements() {

        return schemaStatements;

    }

    public @NotNull String violationUpsert() {

        return violationUpsert;

    }

    public @NotNull String inferenceInsert() {

        return inferenceInsert;

    }

    public @NotNull String bypassUpsert() {

        return bypassUpsert;

    }
}
