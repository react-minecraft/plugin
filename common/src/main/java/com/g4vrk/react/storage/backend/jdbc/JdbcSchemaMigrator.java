package com.g4vrk.react.storage.backend.jdbc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.g4vrk.react.storage.constant.StorageConstant.INFERENCE_TABLE;
import static com.g4vrk.react.storage.constant.StorageConstant.LEGACY_INFERENCE_TABLE;

/**
 * Applies idempotent SQL schema creation, missing column migrations, and legacy inference backfill.
 */
final class JdbcSchemaMigrator {

    private static final List<String> INFERENCE_COLUMN_DEFINITIONS = List.of(
            "model_id VARCHAR(128)",
            "run_id VARCHAR(1024)",
            "decision_threshold DOUBLE",
            "enforcement_enabled BOOLEAN",
            "violations_before DOUBLE",
            "violations_after DOUBLE"
    );

    private JdbcSchemaMigrator() {

        throw new UnsupportedOperationException("Utility class");

    }

    static void initialize(
            final @NotNull Connection connection,
            final @NotNull JdbcDialect dialect
    ) throws SQLException {

        try (final Statement statement = connection.createStatement()) {
            for (final String sql : dialect.schemaStatements()) {
                statement.execute(sql);
            }
        }
        migrateInferenceColumns(connection);
        migrateLegacyHistory(connection);

    }

    private static void migrateInferenceColumns(
            final @NotNull Connection connection
    ) throws SQLException {

        final DatabaseMetaData metadata = connection.getMetaData();
        String tableName = null;
        String tableSchema = null;
        try (final ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if (INFERENCE_TABLE.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    tableName = tables.getString("TABLE_NAME");
                    tableSchema = tables.getString("TABLE_SCHEM");
                    break;
                }
            }
        }
        if (tableName == null) {
            throw new SQLException("Inference history table was not created: " + INFERENCE_TABLE);
        }

        final Set<String> columns = new HashSet<>();
        try (final ResultSet rows = metadata.getColumns(connection.getCatalog(), tableSchema, tableName, "%")) {
            while (rows.next()) {
                columns.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }

        try (final Statement statement = connection.createStatement()) {
            for (final String definition : INFERENCE_COLUMN_DEFINITIONS) {
                final int separator = definition.indexOf(' ');
                final String name = definition.substring(0, separator);
                if (!columns.contains(name)) {
                    try {
                        statement.execute("ALTER TABLE " + INFERENCE_TABLE + " ADD COLUMN " + definition);
                    } catch (final SQLException migrationFailure) {
                        if (!hasColumn(metadata, connection.getCatalog(), tableSchema, tableName, name)) {
                            throw migrationFailure;
                        }
                    }
                    columns.add(name);
                }
            }
        }

    }

    private static boolean hasColumn(
            final @NotNull DatabaseMetaData metadata,
            final @Nullable String catalog,
            final @Nullable String schema,
            final @NotNull String table,
            final @NotNull String column
    ) throws SQLException {

        try (final ResultSet rows = metadata.getColumns(catalog, schema, table, "%")) {
            while (rows.next()) {
                if (column.equalsIgnoreCase(rows.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;

    }

    private static void migrateLegacyHistory(
            final @NotNull Connection connection
    ) throws SQLException {

        boolean legacyExists = false;
        try (final ResultSet tables = connection.getMetaData().getTables(null, null, "%", null)) {
            while (tables.next()) {
                if (LEGACY_INFERENCE_TABLE.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    legacyExists = true;
                    break;
                }
            }
        }
        if (!legacyExists) {
            return;
        }
        final String fields = "id, player_uuid, check_name, recorded_at, probability";
        final String sql = "INSERT INTO " + INFERENCE_TABLE + " (" + fields + ") "
                + "SELECT " + fields + " FROM " + LEGACY_INFERENCE_TABLE + " legacy_rows "
                + "WHERE NOT EXISTS (SELECT 1 FROM " + INFERENCE_TABLE
                + " active_rows WHERE active_rows.id = legacy_rows.id)";
        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }

    }

}
