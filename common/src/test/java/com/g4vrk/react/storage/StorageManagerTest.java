package com.g4vrk.react.storage;

import com.g4vrk.react.check.decay.impl.LinearDecay;
import com.g4vrk.react.storage.backend.StorageBackend;
import com.g4vrk.react.storage.config.DatabaseConfig;
import com.g4vrk.react.storage.config.DatabaseType;
import com.g4vrk.react.storage.model.PlayerStorageData;
import com.g4vrk.react.storage.model.StorageMutation;
import com.g4vrk.react.storage.model.StoredViolation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises write ordering across a database reconnect.
 */
final class StorageManagerTest {

    @Test
    void keepsNewWritesWhenFailedBatchReturnsToFullQueue() throws Exception {

        final DatabaseConfig settings = settings(Duration.ofMillis(10), 2);
        final CountDownLatch writing = new CountDownLatch(1);
        final CountDownLatch failWrite = new CountDownLatch(1);
        final BlockingQueue<List<StorageMutation>> written = new ArrayBlockingQueue<>(1);
        final AtomicBoolean failFirst = new AtomicBoolean(true);
        final StorageManager storage = new StorageManager(NOPLogger.NOP_LOGGER, settings, ignored ->
                new TestBackend(failFirst.getAndSet(false), writing, failWrite, written)
        );
        final UUID player = UUID.randomUUID();

        try {
            storage.start().get(5, TimeUnit.SECONDS);
            storage.saveBypass(player, "aim", Long.MAX_VALUE);
            assertTrue(writing.await(5, TimeUnit.SECONDS));

            storage.saveBypass(player, "aim", -1L);
            storage.saveBypass(player, "other", Long.MAX_VALUE);
            failWrite.countDown();

            assertEquals(
                    List.of(
                            new StorageMutation.Bypass(player, "aim", -1L),
                            new StorageMutation.Bypass(player, "other", Long.MAX_VALUE)
                    ),
                    written.poll(5, TimeUnit.SECONDS)
            );
        } finally {
            failWrite.countDown();
            storage.close(List.of());
        }

    }

    @Test
    void reconnectingPlayerSeesPendingViolationsAndBypassesBeforeFlush() throws Exception {

        final StorageBackend backend = new TestBackend(false, new CountDownLatch(0), new CountDownLatch(0), new ArrayBlockingQueue<>(1));
        final StorageManager storage = new StorageManager(NOPLogger.NOP_LOGGER, settings(Duration.ofDays(1), 2), ignored -> backend);
        final UUID player = UUID.randomUUID();

        try {
            storage.start().get(5, TimeUnit.SECONDS);
            final ViolationLevel level = new ViolationLevel(storage, player, "aim");
            storage.loadPlayer(player, List.of(level)).get(5, TimeUnit.SECONDS);
            level.add(7.0);
            storage.saveBypass(player, "aim", Long.MAX_VALUE);
            final PlayerStorageData loaded = storage.loadPlayer(player).get(5, TimeUnit.SECONDS);

            assertEquals(7.0, loaded.violations().get("aim").value());
            assertEquals(Long.MAX_VALUE, loaded.bypasses().get("aim"));
            assertEquals(PlayerStorageData.EMPTY, storage.loadPlayer(UUID.randomUUID()).get(5, TimeUnit.SECONDS));
        } finally {
            storage.close(List.of());
        }

    }

    @Test
    void hydratesOrderedEditsFromDisconnectedAndCurrentSessions() throws Exception {

        final long storedAt = Long.MAX_VALUE - 1L;
        final RecoveryBackend backend = new RecoveryBackend(Map.of(
                "aim", new StoredViolation(10.0, storedAt),
                "reset", new StoredViolation(20.0, storedAt)
        ), false);
        final StorageManager storage = new StorageManager(NOPLogger.NOP_LOGGER, settings(Duration.ofDays(1), 16), ignored -> backend);
        final UUID player = UUID.randomUUID();

        try {
            storage.start().get(5, TimeUnit.SECONDS);
            final ViolationLevel previous = new ViolationLevel(storage, player, "aim");
            previous.decay(new LinearDecay(3.0));
            previous.add(2.0);
            previous.detach();
            previous.add(100.0);

            final ViolationLevel level = new ViolationLevel(storage, player, "aim");
            final ViolationLevel reset = new ViolationLevel(storage, player, "reset");
            level.add(4.0);
            reset.add(1.0);
            reset.reset();
            reset.add(5.0);

            storage.loadPlayer(player, List.of(level, reset)).get(5, TimeUnit.SECONDS);
            assertEquals(13.0, level.value());
            assertEquals(5.0, reset.value());

            level.add(1.0);
            assertEquals(14.0, storage.loadPlayer(player).get(5, TimeUnit.SECONDS).violations().get("aim").value());
        } finally {
            storage.close(List.of());
        }

    }

    @Test
    void replaysAnUnconfirmedWriteWithTheSameAbsoluteLevel() throws Exception {

        final RecoveryBackend backend = new RecoveryBackend(Map.of("aim", new StoredViolation(23.0, 1L)), true);
        final StorageManager storage = new StorageManager(NOPLogger.NOP_LOGGER, settings(Duration.ofDays(1), 16), ignored -> backend);
        final UUID player = UUID.randomUUID();

        try {
            assertThrows(ExecutionException.class, () -> storage.start().get(5, TimeUnit.SECONDS));
            final ViolationLevel level = new ViolationLevel(storage, player, "aim");
            level.add(3.0);
            level.decay(new LinearDecay(2.0));
            level.add(2.0);
            level.detach();

            backend.online.set(true);
            assertTrue(backend.written.await(5, TimeUnit.SECONDS));
            assertEquals(26.0, backend.levels.get("aim").value());
            assertEquals(2, backend.writeCount.get());

            final ViolationLevel rejoined = new ViolationLevel(storage, player, "aim");
            storage.loadPlayer(player, List.of(rejoined)).get(5, TimeUnit.SECONDS);
            assertEquals(26.0, rejoined.value());
        } finally {
            storage.close(List.of());
        }

    }

    private static @NotNull DatabaseConfig settings(final @NotNull Duration flushInterval, final int bufferCapacity) {

        final Duration retryDelay = Duration.ofMillis(10);
        final Duration timeout = Duration.ofSeconds(5);
        return new DatabaseConfig(
                true,
                DatabaseType.H2,
                Duration.ofDays(1),
                Duration.ofHours(1),
                1,
                10,
                bufferCapacity,
                bufferCapacity,
                flushInterval,
                retryDelay,
                retryDelay,
                2,
                timeout,
                null,
                null
        );
    }

    /**
     * Simulates a database that can commit a batch and lose its acknowledgement.
     */
    private static final class RecoveryBackend implements StorageBackend {

        private final Map<String, StoredViolation> levels;
        private final AtomicBoolean online;
        private final AtomicBoolean failAfterWrite;
        private final CountDownLatch written = new CountDownLatch(1);
        private final AtomicInteger writeCount = new AtomicInteger();

        private RecoveryBackend(final @NotNull Map<String, StoredViolation> levels, final boolean failAfterWrite) {

            this.levels = new ConcurrentHashMap<>(levels);
            this.online = new AtomicBoolean(!failAfterWrite);
            this.failAfterWrite = new AtomicBoolean(failAfterWrite);

        }

        @Override
        public void initialize() {

            if (!online.get()) {
                throw new IllegalStateException("Test database disconnected");
            }

        }

        @Override
        public @NotNull PlayerStorageData loadPlayer(final @NotNull UUID playerUuid, final long historyCutoff) {

            return new PlayerStorageData(levels, List.of(), Map.of());

        }

        @Override
        public void writeBatch(final @NotNull List<StorageMutation> mutations) {

            writeCount.addAndGet(mutations.size());
            for (final StorageMutation mutation : mutations) {
                final StorageMutation.Violation violation = (StorageMutation.Violation) mutation;
                levels.put(violation.check(), new StoredViolation(violation.violations(), violation.updatedAt()));
            }
            if (failAfterWrite.getAndSet(false)) {
                throw new IllegalStateException("Test database lost the commit response");
            }
            written.countDown();

        }

        @Override
        public long deleteHistoryBefore(final long cutoff) {

            return 0L;

        }

        @Override
        public void close() {

        }

    }

    /**
     * Holds the first write until the test fills the queue, then records writes after reconnect.
     */
    private record TestBackend(
            boolean failing,
            @NotNull CountDownLatch writing,
            @NotNull CountDownLatch failWrite,
            @NotNull BlockingQueue<List<StorageMutation>> written
    ) implements StorageBackend {

        @Override
        public void initialize() {

        }

        @Override
        public @NotNull PlayerStorageData loadPlayer(final @NotNull UUID playerUuid, final long historyCutoff) {

            return PlayerStorageData.EMPTY;

        }

        @Override
        public void writeBatch(final @NotNull List<StorageMutation> mutations) throws InterruptedException {

            if (failing) {
                writing.countDown();
                failWrite.await();
                throw new IllegalStateException("Test database disconnected");
            }
            written.add(List.copyOf(mutations));

        }

        @Override
        public long deleteHistoryBefore(final long cutoff) {

            return 0;

        }

        @Override
        public void close() {

        }

    }

}
