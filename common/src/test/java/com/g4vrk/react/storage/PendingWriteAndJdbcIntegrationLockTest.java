/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.storage;

import com.g4vrk.react.storage.backend.jdbc.JdbcConfigTestSupport;
import com.g4vrk.react.storage.backend.jdbc.JdbcStorageBackend;
import com.g4vrk.react.storage.buffer.PendingWriteBuffer;
import com.g4vrk.react.storage.config.DatabaseType;
import com.g4vrk.react.storage.config.JdbcConfig;
import com.g4vrk.react.storage.config.PoolConfig;
import com.g4vrk.react.storage.model.PlayerStorageData;
import com.g4vrk.react.storage.model.StorageMutation;
import com.g4vrk.react.storage.model.StoredInference;
import com.g4vrk.react.storage.model.StoredViolation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies buffered mutation materialization, FIFO eviction, JDBC timestamp ordering, bypass
 * expiration, retention cleanup, and transaction rollback atomicity on SQLite and H2.
 */
final class PendingWriteAndJdbcIntegrationLockTest {

    @Test
    void pendingWriteBufferOverlaysAndPreservesOrderingOnRequeue() {

        assertThrows(IllegalArgumentException.class, () -> new PendingWriteBuffer(0));

        final PendingWriteBuffer buffer = new PendingWriteBuffer(2);
        final UUID player = UUID.randomUUID();
        final UUID other = UUID.randomUUID();

        assertFalse(buffer.enqueue(new StorageMutation.BypassMutation(player, "aim", 5_000L)));
        assertFalse(buffer.enqueue(new StorageMutation.BypassMutation(other, "aim", 5_000L)));
        assertTrue(buffer.enqueue(new StorageMutation.BypassMutation(player, "aim", -1L)));

        final PlayerStorageData overlaid = buffer.overlay(
                player,
                new PlayerStorageData(
                        Map.of("aim", new StoredViolation(4.0D, 100L)),
                        List.of(),
                        Map.of("aim", 9_999L, "other", 9_999L)
                )
        );
        assertNull(overlaid.bypasses().get("aim"));
        assertEquals(9_999L, overlaid.bypasses().get("other"));

        final List<StorageMutation> drained = buffer.drainBatch(10);
        assertEquals(2, drained.size());
        assertFalse(buffer.hasPending());

        assertFalse(buffer.enqueue(new StorageMutation.BypassMutation(player, "latest", 8_000L)));
        assertTrue(buffer.requeue(drained));
        final List<StorageMutation> finalBatch = buffer.drainBatch(10);
        assertEquals(2, finalBatch.size());
        assertEquals(drained.get(1), finalBatch.get(0));
        assertEquals("latest", ((StorageMutation.BypassMutation) finalBatch.get(1)).check());

    }

    @Test
    void sqlitePreventsStaleViolationOverwriteAndCleansExpiredData(@TempDir final Path dir) throws Exception {

        assertBackendInvariants(
                DatabaseType.SQLITE,
                "org.sqlite.JDBC",
                "jdbc:sqlite:" + dir.resolve("lock.db")
        );

    }

    @Test
    void h2PreventsStaleViolationOverwriteAndCleansExpiredData() throws Exception {

        final String name = UUID.randomUUID().toString().replace("-", "");
        assertBackendInvariants(
                DatabaseType.H2,
                "org.h2.Driver",
                "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1"
        );

    }

    private static void assertBackendInvariants(
            final @NotNull DatabaseType type,
            final @NotNull String driver,
            final @NotNull String url
    ) throws Exception {

        final JdbcStorageBackend backend = new JdbcStorageBackend(type, config(url, driver));
        final UUID player = UUID.randomUUID();
        final long future = System.currentTimeMillis() + 60_000L;
        final long expired = System.currentTimeMillis() - 60_000L;
        try {
            backend.initialize();
            backend.writeBatch(List.of(
                    new StorageMutation.Violation(player, "aim-ai", 12.0D, 200L),
                    new StorageMutation.Violation(player, "aim-ai", 2.0D, 150L),
                    new StorageMutation.BypassMutation(player, "aim-ai", future),
                    new StorageMutation.BypassMutation(player, "old", expired),
                    new StorageMutation.BypassMutation(player, "lifted", future),
                    new StorageMutation.BypassMutation(player, "lifted", -1L),
                    new StorageMutation.Inference(player, UUID.randomUUID(), new StoredInference(50L, "aim-ai", 0.2D, "m", "r", 0.5D, false, 0.0D, 0.0D)),
                    new StorageMutation.Inference(player, UUID.randomUUID(), new StoredInference(500L, "aim-ai", 0.9D, "m", "r", 0.5D, true, 0.0D, 1.0D))
            ));

            final PlayerStorageData data = backend.loadPlayer(player, 100L);
            assertEquals(12.0D, data.violations().get("aim-ai").value(), 1.0E-6D);
            assertEquals(future, data.bypasses().get("aim-ai"));
            assertNull(data.bypasses().get("old"));
            assertNull(data.bypasses().get("lifted"));
            assertEquals(1, data.inferenceHistory().size());
            assertEquals(500L, data.inferenceHistory().get(0).timestamp());

            final long deleted = backend.deleteHistoryBefore(250L);
            assertEquals(1L, deleted);
            assertEquals(1, backend.loadPlayer(player, 0L).inferenceHistory().size());
        } finally {
            backend.close();
        }

        try (Connection connection = DriverManager.getConnection(url)) {
            JdbcConfigTestSupport.assertTransactionRollsBack(connection);
        }

    }

    private static @NotNull JdbcConfig config(
            final @NotNull String jdbcUrl,
            final @NotNull String driver
    ) {

        final Duration second = Duration.ofSeconds(1);
        return new JdbcConfig(
                jdbcUrl,
                driver,
                "",
                "",
                new PoolConfig(2, 1, second, second, Duration.ofMinutes(1), Duration.ofMinutes(5)),
                Map.of()
        );

    }

}
