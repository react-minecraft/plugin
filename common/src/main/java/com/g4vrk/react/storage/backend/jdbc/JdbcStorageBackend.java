package com.g4vrk.react.storage.backend.jdbc;

import com.g4vrk.react.storage.backend.StorageBackend;
import com.g4vrk.react.storage.config.DatabaseType;
import com.g4vrk.react.storage.config.JdbcConfig;
import com.g4vrk.react.storage.config.PoolConfig;
import com.g4vrk.react.storage.model.PlayerStorageData;
import com.g4vrk.react.storage.model.StorageMutation;
import com.g4vrk.react.storage.model.StoredInference;
import com.g4vrk.react.storage.model.StoredViolation;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.g4vrk.react.storage.constant.StorageConstant.BYPASS_TABLE;
import static com.g4vrk.react.storage.constant.StorageConstant.INFERENCE_TABLE;
import static com.g4vrk.react.storage.constant.StorageConstant.VIOLATIONS_TABLE;

/**
 * Stores player data in a JDBC database.
 */
public final class JdbcStorageBackend implements StorageBackend {

    private static final String LOAD_VIOLATIONS = "SELECT check_name, violations, updated_at FROM " + VIOLATIONS_TABLE + " WHERE player_uuid = ?";
    private static final String LOAD_INFERENCE = "SELECT check_name, recorded_at, probability, model_id, run_id, decision_threshold, enforcement_enabled, violations_before, violations_after FROM " + INFERENCE_TABLE + " WHERE player_uuid = ? AND recorded_at >= ? ORDER BY recorded_at ASC";
    private static final String LOAD_BYPASSES = "SELECT check_name, expires_at FROM " + BYPASS_TABLE + " WHERE player_uuid = ? AND expires_at > ?";
    private static final String DELETE_BYPASS = "DELETE FROM " + BYPASS_TABLE + " WHERE player_uuid = ? AND check_name = ?";
    private static final String DELETE_EXPIRED_BYPASSES = "DELETE FROM " + BYPASS_TABLE + " WHERE expires_at <= ?";
    private static final String DELETE_OLD_INFERENCE = "DELETE FROM " + INFERENCE_TABLE + " WHERE recorded_at < ?";

    private final HikariDataSource dataSource;
    private final JdbcExecutor executor;
    private final JdbcDialect dialect;

    public JdbcStorageBackend(
            final @NotNull DatabaseType type,
            final @NotNull JdbcConfig settings
    ) {

        this.dialect = JdbcDialect.forType(type);
        this.dataSource = new HikariDataSource(hikariConfig(type, settings));
        this.executor = new JdbcExecutor(dataSource);

    }

    @Override
    public void initialize() throws Exception {

        executor.transaction(connection -> {
            JdbcSchemaMigrator.initialize(connection, dialect);
            return null;
        });

    }

    @Override
    public @NotNull PlayerStorageData loadPlayer(
            final @NotNull UUID playerUuid,
            final long historyCutoff
    ) throws SQLException {

        return executor.query(connection -> new PlayerStorageData(
                loadViolations(connection, playerUuid),
                loadInference(connection, playerUuid, historyCutoff),
                loadBypasses(connection, playerUuid)
        ));

    }

    @Override
    public void writeBatch(final @NotNull List<StorageMutation> mutations) throws Exception {

        if (mutations.isEmpty()) {
            return;
        }

        executor.transaction(connection -> {
            try (final PreparedStatement violationStatement = connection.prepareStatement(dialect.violationUpsert());
                 final PreparedStatement inferenceStatement = connection.prepareStatement(dialect.inferenceInsert());
                 final PreparedStatement bypassStatement = connection.prepareStatement(dialect.bypassUpsert());
                 final PreparedStatement liftStatement = connection.prepareStatement(DELETE_BYPASS)) {
                boolean hasViolations = false;
                boolean hasInference = false;
                boolean hasBypasses = false;
                boolean hasLifts = false;
                final Map<UUID, Map<String, StorageMutation.BypassMutation>> bypasses = new HashMap<>();

                for (final StorageMutation mutation : mutations) {
                    if (mutation instanceof final StorageMutation.Violation violation) {
                        bindViolation(violationStatement, violation);
                        violationStatement.addBatch();
                        hasViolations = true;
                    } else if (mutation instanceof final StorageMutation.Inference inference) {
                        bindInference(inferenceStatement, inference);
                        inferenceStatement.addBatch();
                        hasInference = true;
                    } else if (mutation instanceof final StorageMutation.BypassMutation bypass) {
                        bypasses.computeIfAbsent(bypass.playerUuid(), key -> new HashMap<>())
                                .put(bypass.check(), bypass);
                    }
                }

                for (final Map<String, StorageMutation.BypassMutation> playerBypasses : bypasses.values()) {
                    for (final StorageMutation.BypassMutation bypass : playerBypasses.values()) {
                        final PreparedStatement statement = bypass.lifted() ? liftStatement : bypassStatement;
                        statement.setString(1, bypass.playerUuid().toString());
                        statement.setString(2, bypass.check());
                        if (bypass.lifted()) {
                            hasLifts = true;
                        } else {
                            statement.setLong(3, bypass.until());
                            hasBypasses = true;
                        }
                        statement.addBatch();
                    }
                }

                if (hasViolations) {
                    violationStatement.executeBatch();
                }
                if (hasInference) {
                    inferenceStatement.executeBatch();
                }
                if (hasLifts) {
                    liftStatement.executeBatch();
                }
                if (hasBypasses) {
                    bypassStatement.executeBatch();
                }
            }
            return null;
        });

    }

    @Override
    public long deleteHistoryBefore(final long cutoff) throws Exception {

        return executor.query(connection -> {
            try (final PreparedStatement statement = connection.prepareStatement(DELETE_OLD_INFERENCE);
                 final PreparedStatement bypasses = connection.prepareStatement(DELETE_EXPIRED_BYPASSES)) {
                bypasses.setLong(1, System.currentTimeMillis());
                bypasses.executeUpdate();
                statement.setLong(1, cutoff);
                return statement.executeUpdate();
            }
        });

    }

    @Override
    public void close() {

        dataSource.close();

    }

    private @NotNull Map<String, StoredViolation> loadViolations(
            final @NotNull Connection connection,
            final @NotNull UUID playerUuid
    ) throws SQLException {

        final Map<String, StoredViolation> values = new LinkedHashMap<>();
        try (final PreparedStatement statement = connection.prepareStatement(LOAD_VIOLATIONS)) {
            statement.setString(1, playerUuid.toString());
            try (final ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.put(
                            result.getString("check_name"),
                            new StoredViolation(result.getDouble("violations"), result.getLong("updated_at"))
                    );
                }
            }
        }
        return values;

    }

    private @NotNull List<StoredInference> loadInference(
            final @NotNull Connection connection,
            final @NotNull UUID playerUuid,
            final long historyCutoff
    ) throws SQLException {

        final List<StoredInference> values = new ArrayList<>();
        try (final PreparedStatement statement = connection.prepareStatement(LOAD_INFERENCE)) {
            statement.setString(1, playerUuid.toString());
            statement.setLong(2, historyCutoff);
            try (final ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new StoredInference(
                            result.getLong("recorded_at"),
                            result.getString("check_name"),
                            result.getDouble("probability"),
                            result.getString("model_id"),
                            result.getString("run_id"),
                            nullableDouble(result, "decision_threshold"),
                            nullableBoolean(result, "enforcement_enabled"),
                            nullableDouble(result, "violations_before"),
                            nullableDouble(result, "violations_after")
                    ));
                }
            }
        }
        return values;

    }

    private @NotNull Map<String, Long> loadBypasses(
            final @NotNull Connection connection,
            final @NotNull UUID playerUuid
    ) throws SQLException {

        final Map<String, Long> values = new LinkedHashMap<>();
        try (final PreparedStatement statement = connection.prepareStatement(LOAD_BYPASSES)) {
            statement.setString(1, playerUuid.toString());
            statement.setLong(2, System.currentTimeMillis());
            try (final ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.put(result.getString("check_name"), result.getLong("expires_at"));
                }
            }
        }
        return values;

    }

    private void bindViolation(
            final @NotNull PreparedStatement statement,
            final @NotNull StorageMutation.Violation violation
    ) throws SQLException {

        statement.setString(1, violation.playerUuid().toString());
        statement.setString(2, violation.check());
        statement.setDouble(3, violation.violations());
        statement.setLong(4, violation.updatedAt());

    }

    private void bindInference(
            final @NotNull PreparedStatement statement,
            final @NotNull StorageMutation.Inference inference
    ) throws SQLException {

        final StoredInference value = inference.value();
        statement.setString(1, inference.id().toString());
        statement.setString(2, inference.playerUuid().toString());
        statement.setString(3, value.check());
        statement.setLong(4, value.timestamp());
        statement.setDouble(5, value.probability());
        statement.setString(6, value.modelId());
        statement.setString(7, value.runId());
        bindNullableDouble(statement, 8, value.threshold());
        bindNullableBoolean(statement, 9, value.enforcement());
        bindNullableDouble(statement, 10, value.violationsBefore());
        bindNullableDouble(statement, 11, value.violationsAfter());

    }

    private static @Nullable Double nullableDouble(
            final @NotNull ResultSet result,
            final @NotNull String column
    ) throws SQLException {

        final double value = result.getDouble(column);
        return result.wasNull() ? null : value;

    }

    private static @Nullable Boolean nullableBoolean(
            final @NotNull ResultSet result,
            final @NotNull String column
    ) throws SQLException {

        final boolean value = result.getBoolean(column);
        return result.wasNull() ? null : value;

    }

    private static void bindNullableDouble(
            final @NotNull PreparedStatement statement,
            final int index,
            final @Nullable Double value
    ) throws SQLException {

        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }

    }

    private static void bindNullableBoolean(
            final @NotNull PreparedStatement statement,
            final int index,
            final @Nullable Boolean value
    ) throws SQLException {

        if (value == null) {
            statement.setNull(index, Types.BOOLEAN);
        } else {
            statement.setBoolean(index, value);
        }

    }

    private static @NotNull HikariConfig hikariConfig(
            final @NotNull DatabaseType type,
            final @NotNull JdbcConfig settings
    ) {

        final PoolConfig pool = settings.pool();
        final HikariConfig config = new HikariConfig();
        config.setPoolName("React-" + type.name());
        config.setJdbcUrl(settings.jdbcUrl());
        config.setDriverClassName(settings.driverClassName());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(pool.maximumPoolSize());
        config.setMinimumIdle(Math.min(pool.minimumIdle(), pool.maximumPoolSize()));
        config.setConnectionTimeout(pool.connectionTimeout().toMillis());
        config.setValidationTimeout(pool.validationTimeout().toMillis());
        config.setIdleTimeout(pool.idleTimeout().toMillis());
        config.setMaxLifetime(pool.maxLifetime().toMillis());
        config.setAutoCommit(true);
        settings.properties().forEach(config::addDataSourceProperty);
        return config;

    }
}
