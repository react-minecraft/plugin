package com.g4vrk.react.storage;

import com.g4vrk.react.storage.backend.StorageBackend;
import com.g4vrk.react.storage.config.DatabaseSettings;
import com.g4vrk.react.storage.config.DatabaseType;
import com.g4vrk.react.storage.model.PlayerStorageData;
import com.g4vrk.react.storage.model.StorageMutation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises write ordering across a database reconnect.
 */
final class StorageManagerTest {

    @Test
    void keepsNewWritesWhenFailedBatchReturnsToFullQueue() throws Exception {

        final Duration retryDelay = Duration.ofMillis(10);
        final Duration timeout = Duration.ofSeconds(5);
        final DatabaseSettings settings = new DatabaseSettings(
                true,
                DatabaseType.H2,
                Duration.ofDays(1),
                Duration.ofHours(1),
                1,
                10,
                2,
                2,
                retryDelay,
                retryDelay,
                timeout,
                2,
                timeout,
                null,
                null
        );
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
