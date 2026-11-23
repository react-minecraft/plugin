package com.g4vrk.react.storage.backend.jdbc;

import com.g4vrk.react.storage.config.DatabaseType;
import com.g4vrk.react.storage.config.JdbcConfig;
import com.g4vrk.react.storage.config.PoolConfig;
import com.g4vrk.react.storage.model.StorageMutation;
import com.g4vrk.react.storage.model.StoredInference;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that old inference rows survive the additive provenance migration.
 */
class JdbcInferenceHistoryMigrationTest {

    @Test
    void migratesSqliteRowsAndRoundTripsNewMetadata(@TempDir final Path directory) throws Exception {

        final Path databaseFile = directory.resolve("history.db");
        assertMigrationRoundTrip(DatabaseType.SQLITE, "org.sqlite.JDBC", "jdbc:sqlite:" + databaseFile);

    }

    @Test
    void migratesH2RowsAndRoundTripsNewMetadata() throws Exception {

        final String databaseName = UUID.randomUUID().toString().replace("-", "");
        assertMigrationRoundTrip(DatabaseType.H2, "org.h2.Driver", "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");

    }

    private static void assertMigrationRoundTrip(
            final DatabaseType type,
            final String driver,
            final String jdbcUrl
    ) throws Exception {

        final UUID player = UUID.randomUUID();
        try (final Connection connection = DriverManager.getConnection(jdbcUrl);
             final Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE react_inference_scores (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, check_name VARCHAR(64) NOT NULL, recorded_at BIGINT NOT NULL, probability REAL NOT NULL)");
            try (final PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO react_inference_scores (id, player_uuid, check_name, recorded_at, probability) VALUES (?, ?, ?, ?, ?)")) {
                insert.setString(1, UUID.randomUUID().toString());
                insert.setString(2, player.toString());
                insert.setString(3, "aim-ai");
                insert.setLong(4, 100L);
                insert.setDouble(5, 0.6D);
                insert.executeUpdate();
            }
        }

        final JdbcStorageBackend backend = new JdbcStorageBackend(type, settings(jdbcUrl, driver));
        try {
            backend.initialize();
            final StoredInference expected = new StoredInference(
                    200L, "aim-ai", 0.9D, "flower/baseline", "run-42", 0.8D, false, 1.0D, 1.0D);
            backend.writeBatch(List.of(new StorageMutation.Inference(player, UUID.randomUUID(), expected)));

            final List<StoredInference> history = backend.loadPlayer(player, 0L).inferenceHistory();
            assertEquals(2, history.size());
            assertEquals(100L, history.get(0).timestamp());
            assertEquals(0.6D, history.get(0).probability(), 1.0E-6D);
            assertNull(history.get(0).modelId());
            assertNull(history.get(0).runId());
            final StoredInference migrated = history.get(1);
            assertEquals(expected.timestamp(), migrated.timestamp());
            assertEquals(expected.check(), migrated.check());
            assertEquals(expected.probability(), migrated.probability(), 1.0E-6D);
            assertEquals(expected.modelId(), migrated.modelId());
            assertEquals(expected.runId(), migrated.runId());
            assertEquals(expected.threshold(), migrated.threshold());
            assertEquals(expected.enforcement(), migrated.enforcement());
            assertEquals(expected.violationsBefore(), migrated.violationsBefore());
            assertEquals(expected.violationsAfter(), migrated.violationsAfter());
        } finally {
            backend.close();
        }

    }

    private static JdbcConfig settings(final String jdbcUrl, final String driver) {

        return new JdbcConfig(
                jdbcUrl,
                driver,
                "",
                "",
                new PoolConfig(1, 1, Duration.ofSeconds(10), Duration.ofSeconds(5),
                        Duration.ofMinutes(1), Duration.ofMinutes(30)),
                Map.of()
        );

    }

}
