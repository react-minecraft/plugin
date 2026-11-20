package com.g4vrk.react.storage;

import com.g4vrk.react.check.Check;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.storage.backend.StorageBackend;
import com.g4vrk.react.storage.backend.StorageBackendFactory;
import com.g4vrk.react.storage.config.DatabaseSettings;
import com.g4vrk.react.storage.model.PlayerStorageData;
import com.g4vrk.react.storage.model.StorageMutation;
import com.g4vrk.react.storage.model.StoredInference;
import com.g4vrk.react.storage.model.StoredViolation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static com.g4vrk.react.storage.constant.StorageConstants.FAILURE_LOG_INTERVAL_MILLIS;

/**
 * Owns the storage connection, its workers and the bounded queue of pending writes.
 */
public final class StorageManager {

    private final Logger logger;
    private final DatabaseSettings settings;
    private final Function<DatabaseSettings, StorageBackend> backendFactory;

    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor scheduler;
    private final Deque<StorageMutation> pendingWrites = new ArrayDeque<>();
    private final Object pendingWriteLock = new Object();
    private final AtomicBoolean available = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean flushRunning = new AtomicBoolean();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicLong reconnectDelayMillis;
    private final AtomicLong lastFailureLog = new AtomicLong();
    private final ReentrantReadWriteLock backendLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock flushLock = new ReentrantReadWriteLock();
    private final Object availabilityMonitor = new Object();

    private volatile @Nullable StorageBackend backend;
    private volatile CompletableFuture<Void> initialization = CompletableFuture.completedFuture(null);

    public StorageManager(
            final @NotNull Logger logger,
            final @NotNull DatabaseSettings settings
    ) {

        this(logger, settings, new StorageBackendFactory()::create);

    }

    StorageManager(
            final @NotNull Logger logger,
            final @NotNull DatabaseSettings settings,
            final @NotNull Function<DatabaseSettings, StorageBackend> backendFactory
    ) {
        this.logger = logger;
        this.settings = settings;
        this.backendFactory = backendFactory;
        this.reconnectDelayMillis = new AtomicLong(settings.retryInitialDelay().toMillis());
        this.executor = new ThreadPoolExecutor(
                settings.executorThreads(),
                settings.executorThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.executorQueueCapacity()),
                threadFactory("React-Database-Worker"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.scheduler = new ScheduledThreadPoolExecutor(1, threadFactory("React-Database-Scheduler"));
        this.scheduler.setRemoveOnCancelPolicy(true);
    }

    public @NotNull CompletableFuture<Void> start() {

        if (!started.compareAndSet(false, true) || !settings.enabled()) {
            return initialization;
        }

        initialization = runAsync(this::connect);

        final long flushMillis = settings.flushInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::requestFlush, flushMillis, flushMillis, TimeUnit.MILLISECONDS);

        final long cleanupMillis = settings.cleanupInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::requestCleanup, cleanupMillis, cleanupMillis, TimeUnit.MILLISECONDS);

        initialization.whenComplete((ignored, failure) -> {
            if (failure != null) {
                logFailure("Could not initialize " + settings.type() + " storage; continuing in fail-open mode", failure);
                scheduleReconnect();
            } else {
                logger.info("{} storage initialized successfully", settings.type());
            }
        });
        return initialization;

    }

    public @NotNull CompletableFuture<PlayerStorageData> loadPlayer(final @NotNull UUID playerUuid) {

        return loadPlayer(playerUuid, List.of());

    }

    public @NotNull CompletableFuture<PlayerStorageData> loadPlayer(
            final @NotNull UUID playerUuid,
            final @NotNull List<ViolationLevel> levels
    ) {

        if (!settings.enabled()) {
            return CompletableFuture.completedFuture(PlayerStorageData.EMPTY);
        }
        if (closing.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database storage is closed"));
        }

        final CompletableFuture<Void> readiness;
        if (available.get()) {
            readiness = CompletableFuture.completedFuture(null);
        } else if (!initialization.isDone()) {
            readiness = initialization;
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException("Database is unavailable"));
        }

        return readiness.thenCompose(ignored -> supplyAsync(() -> loadWithPendingWrites(playerUuid, levels))).whenComplete((ignored, failure) -> {
            if (failure != null && backendFailure(failure)) {
                markUnavailable(failure);
            }
        });

    }

    private @NotNull PlayerStorageData loadWithPendingWrites(
            final @NotNull UUID playerUuid,
            final @NotNull List<ViolationLevel> levels
    ) throws Exception {

        /*
         * A queued write may be newer than the database row. Exclude the flush while
         * reading so that in-flight writes cannot disappear between the two reads.
         */
        flushLock.readLock().lock();
        try {
            final PlayerStorageData stored = withBackend(storage -> storage.loadPlayer(playerUuid, historyCutoff()));
            synchronized (pendingWriteLock) {
                final PlayerStorageData loaded = applyPendingWrites(playerUuid, stored);
                for (final ViolationLevel level : levels) {
                    level.load(loaded.violations().get(level.check()));
                }
                return loaded;
            }
        } finally {
            flushLock.readLock().unlock();
        }

    }

    private @NotNull PlayerStorageData applyPendingWrites(
            final @NotNull UUID playerUuid,
            final @NotNull PlayerStorageData stored
    ) {

        Map<String, StoredViolation> violations = null;
        Map<String, Long> bypasses = null;
        for (final StorageMutation mutation : pendingWrites) {
            if (!mutation.playerUuid().equals(playerUuid)) {
                continue;
            }
            if (mutation instanceof final StorageMutation.Violation violation) {
                if (violations == null) {
                    violations = new HashMap<>(stored.violations());
                }
                putViolation(violations, violation);
            } else if (mutation instanceof final StorageMutation.PendingViolation pending) {
                if (violations == null) {
                    violations = new HashMap<>(stored.violations());
                }
                final StorageMutation.Violation violation = pending.materialize(violations.get(pending.check()));
                putViolation(violations, violation);
            } else if (mutation instanceof final StorageMutation.Bypass bypass) {
                if (bypasses == null) {
                    bypasses = new HashMap<>(stored.bypasses());
                }
                if (bypass.lifted()) {
                    bypasses.remove(bypass.check());
                } else {
                    bypasses.put(bypass.check(), bypass.until());
                }
            }
        }
        if (violations == null && bypasses == null) {
            return stored;
        }
        return new PlayerStorageData(
                violations == null ? stored.violations() : violations,
                stored.inferenceHistory(),
                bypasses == null ? stored.bypasses() : bypasses
        );

    }

    @NotNull Object pendingWriteLock() {

        return pendingWriteLock;

    }

    public void saveInference(
            final @NotNull UUID playerUuid,
            final @NotNull StoredInference inference
    ) {

        if (!settings.enabled()) {
            return;
        }
        enqueue(new StorageMutation.Inference(playerUuid, UUID.randomUUID(), inference), false);

    }

    /**
     * Stores a temporary bypass, or lifts it.
     *
     * @param playerUuid the player
     * @param check      the check id, or {@code *} for every check
     * @param until      when it ends in epoch millis, negative to lift it
     */
    public void saveBypass(
            final @NotNull UUID playerUuid,
            final @NotNull String check,
            final long until
    ) {

        enqueue(new StorageMutation.Bypass(playerUuid, check, until), false);

    }

    public void detachPlayer(final @NotNull TrackedPlayerImpl player) {

        for (final Check check : player.checks()) {
            check.violationLevel().detach();
        }

    }

    private void saveSnapshot(final @NotNull TrackedPlayerImpl player) {

        for (final Check check : player.checks()) {
            check.violationLevel().saveSnapshot(true);
        }

    }

    public long historyCutoff() {

        return System.currentTimeMillis() - settings.historyRetention().toMillis();

    }

    public @NotNull Duration historyRetention() {

        return settings.historyRetention();

    }

    public boolean enabled() {

        return settings.enabled();

    }

    public boolean available() {

        return available.get();

    }

    public boolean closing() {

        return closing.get();

    }

    public void close(final @NotNull Collection<TrackedPlayerImpl> players) {

        if (!closing.compareAndSet(false, true)) {
            return;
        }

        if (settings.enabled()) {
            players.forEach(this::saveSnapshot);
            final CompletableFuture<Void> flush = initialization
                    .handle((ignored, failure) -> null)
                    .thenCompose(ignored -> runAsync(this::flushAll));
            try {
                flush.get(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (final Exception ex) {
                logFailure("Could not flush all database writes before shutdown", ex);
            }
        }

        scheduler.shutdownNow();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        backendLock.writeLock().lock();
        try {
            synchronized (availabilityMonitor) {
                available.set(false);
            }
            if (backend != null) {
                backend.close();
                backend = null;
            }
        } catch (final Exception ex) {
            logFailure("Could not close the database backend", ex);
        } finally {
            backendLock.writeLock().unlock();
        }

    }

    void enqueue(final @NotNull StorageMutation mutation, final boolean allowClosing) {

        if (!settings.enabled()) {
            return;
        }

        final boolean discarded;
        synchronized (pendingWriteLock) {
            if (closing.get() && !allowClosing) {
                return;
            }

            discarded = pendingWrites.size() == settings.pendingWriteCapacity();
            if (discarded) {
                pendingWrites.removeFirst();
            }
            pendingWrites.addLast(mutation);
        }
        if (discarded) {
            logFailure("Database write buffer is full; the oldest unsaved operation was discarded", null);
        }

    }

    private boolean hasPendingWrites() {

        synchronized (pendingWriteLock) {
            return !pendingWrites.isEmpty();
        }

    }

    private void requestFlush() {

        if (!settings.enabled() || closing.get() || !available.get() || !hasPendingWrites()) {
            return;
        }
        if (!flushRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    flushOneBatch();
                } finally {
                    flushRunning.set(false);
                }
            });
        } catch (final RejectedExecutionException ex) {
            flushRunning.set(false);
            logFailure("Database executor queue is full; writes remain buffered", ex);
        }

    }

    private boolean flushOneBatch() {

        flushLock.writeLock().lock();
        try {
            final List<StorageMutation> drained = drainBatch();
            if (drained.isEmpty()) {
                return false;
            }

            List<StorageMutation> batch = drained;
            try {
                batch = materializeBatch(drained);
                final List<StorageMutation> writes = batch;
                withBackend(storage -> {
                    storage.writeBatch(writes);
                    return null;
                });
                return true;
            } catch (final Exception ex) {
                requeue(batch);
                markUnavailable(ex);
                return false;
            }
        } finally {
            flushLock.writeLock().unlock();
        }

    }

    private @NotNull List<StorageMutation> materializeBatch(final @NotNull List<StorageMutation> batch) throws Exception {

        Map<UUID, Map<String, BatchViolation>> levels = null;
        for (final StorageMutation mutation : batch) {
            if (mutation instanceof final StorageMutation.PendingViolation pending) {
                if (levels == null) {
                    levels = new HashMap<>();
                }
                if (!levels.containsKey(pending.playerUuid())) {
                    final PlayerStorageData stored = withBackend(storage -> storage.loadPlayer(pending.playerUuid(), Long.MAX_VALUE));
                    final Map<String, BatchViolation> base = new HashMap<>();
                    stored.violations().forEach((check, value) -> base.put(check, new BatchViolation(value, -1)));
                    levels.put(pending.playerUuid(), base);
                }
            }
        }
        if (levels == null) {
            return batch;
        }

        /*
         * Keep the latest materialized write per check at its original position in the batch.
         */
        final List<StorageMutation> materialized = new ArrayList<>(batch);
        for (int index = 0; index < batch.size(); index++) {
            final StorageMutation mutation = batch.get(index);
            final Map<String, BatchViolation> current = levels.get(mutation.playerUuid());
            if (current == null) {
                continue;
            }
            final StorageMutation.Violation violation;
            if (mutation instanceof final StorageMutation.PendingViolation pending) {
                final BatchViolation previous = current.get(pending.check());
                violation = pending.materialize(previous == null ? null : previous.value());
            } else if (mutation instanceof final StorageMutation.Violation absolute) {
                violation = absolute;
            } else {
                continue;
            }
            final BatchViolation previous = current.get(violation.check());
            if (previous == null || violation.updatedAt() >= previous.value().updatedAt()) {
                if (previous != null && previous.index() >= 0) {
                    materialized.set(previous.index(), null);
                }
                current.put(violation.check(), new BatchViolation(new StoredViolation(violation.violations(), violation.updatedAt()), index));
                materialized.set(index, violation);
            } else {
                materialized.set(index, null);
            }
        }
        materialized.removeIf(Objects::isNull);
        return materialized;

    }

    /**
     * A recovered check level and the position of its pending absolute write, if any.
     */
    private record BatchViolation(@NotNull StoredViolation value, int index) {
    }

    private static void putViolation(
            final @NotNull Map<String, StoredViolation> levels,
            final @NotNull StorageMutation.Violation violation
    ) {

        final StoredViolation previous = levels.get(violation.check());
        if (previous == null || violation.updatedAt() >= previous.updatedAt()) {
            levels.put(violation.check(), new StoredViolation(violation.violations(), violation.updatedAt()));
        }

    }

    private void flushAll() {

        while (available.get() && hasPendingWrites()) {
            if (!flushOneBatch()) {
                break;
            }
        }

    }

    private @NotNull List<StorageMutation> drainBatch() {

        final List<StorageMutation> batch = new ArrayList<>(settings.batchSize());
        synchronized (pendingWriteLock) {
            while (batch.size() < settings.batchSize()) {
                final StorageMutation mutation = pendingWrites.pollFirst();
                if (mutation == null) {
                    break;
                }
                batch.add(mutation);
            }
        }
        return batch;

    }

    private void requeue(final @NotNull List<StorageMutation> batch) {

        final boolean discarded;
        synchronized (pendingWriteLock) {
            final int availableSlots = settings.pendingWriteCapacity() - pendingWrites.size();
            final int firstRetained = Math.max(0, batch.size() - availableSlots);
            for (int index = batch.size() - 1; index >= firstRetained; index--) {
                pendingWrites.addFirst(batch.get(index));
            }
            discarded = firstRetained > 0;
        }
        if (discarded) {
            logFailure("Database write buffer is full after a failed batch; the oldest unsaved operations were discarded", null);
        }

    }

    private void requestCleanup() {

        if (!available.get() || closing.get()) {
            return;
        }
        runAsync(() -> {
            try {
                withBackend(storage -> {
                    storage.deleteHistoryBefore(historyCutoff());
                    return null;
                });
            } catch (final Exception ex) {
                markUnavailable(ex);
            }
        });

    }

    private void connect() throws Exception {

        final StorageBackend replacement = backendFactory.apply(settings);
        try {
            replacement.initialize();
            replacement.deleteHistoryBefore(historyCutoff());
        } catch (final Exception ex) {
            try {
                replacement.close();
            } catch (final Exception closeFailure) {
                ex.addSuppressed(closeFailure);
            }
            throw ex;
        }

        backendLock.writeLock().lock();
        try {
            if (closing.get()) {
                replacement.close();
                return;
            }
            final StorageBackend previous = backend;
            backend = replacement;
            synchronized (availabilityMonitor) {
                available.set(true);
                reconnectDelayMillis.set(settings.retryInitialDelay().toMillis());
            }
            if (previous != null) {
                try {
                    previous.close();
                } catch (final Exception ex) {
                    logFailure("Could not close the replaced database backend", ex);
                }
            }
        } finally {
            backendLock.writeLock().unlock();
        }

    }

    private void markUnavailable(final @NotNull Throwable failure) {

        synchronized (availabilityMonitor) {
            if (!settings.enabled() || closing.get()) {
                return;
            }
            available.set(false);
        }
        logFailure("Database operation failed; continuing in fail-open mode", failure);
        scheduleReconnect();

    }

    private void scheduleReconnect() {

        if (closing.get() || !settings.enabled() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }

        final long delay = reconnectDelayMillis.getAndUpdate(current -> Math.min(
                settings.retryMaximumDelay().toMillis(),
                Math.max(current + 1L, (long) (current * settings.retryMultiplier()))
        ));
        try {
            scheduler.schedule(() -> {
                reconnectScheduled.set(false);
                runAsync(this::connect).whenComplete((ignored, failure) -> {
                    if (failure == null && !closing.get()) {
                        logger.info("{} storage connection restored", settings.type());
                        requestFlush();
                    } else if (failure != null) {
                        logFailure("Could not restore the database connection", failure);
                        scheduleReconnect();
                    }
                });
            }, delay, TimeUnit.MILLISECONDS);
        } catch (final RejectedExecutionException ex) {
            reconnectScheduled.set(false);
            if (!closing.get()) {
                logFailure("Could not schedule a database reconnect", ex);
            }
        }

    }

    private <T> T withBackend(final @NotNull BackendFunction<T> action) throws Exception {

        backendLock.readLock().lock();
        try {
            if (!available.get() || backend == null) {
                throw new IllegalStateException("Database is unavailable");
            }
            return action.apply(backend);
        } finally {
            backendLock.readLock().unlock();
        }

    }

    private @NotNull CompletableFuture<Void> runAsync(final @NotNull CheckedRunnable action) {

        return supplyAsync(() -> {
            action.run();
            return null;
        });

    }

    private boolean backendFailure(final @NotNull Throwable failure) {

        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return !(cause instanceof RejectedExecutionException);

    }

    private <T> @NotNull CompletableFuture<T> supplyAsync(final @NotNull CheckedSupplier<T> supplier) {

        final CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(supplier.get());
                } catch (final Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (final RejectedExecutionException ex) {
            future.completeExceptionally(ex);
        }
        return future;

    }

    private void logFailure(final @NotNull String message, final @Nullable Throwable failure) {

        final long now = System.currentTimeMillis();
        final long previous = lastFailureLog.get();
        if (now - previous < FAILURE_LOG_INTERVAL_MILLIS || !lastFailureLog.compareAndSet(previous, now)) {
            return;
        }
        if (failure == null) {
            logger.warn(message);
        } else {
            logger.warn(message, failure);
        }

    }

    private static @NotNull ThreadFactory threadFactory(final @NotNull String prefix) {

        final AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            final Thread thread = new Thread(runnable, prefix + '-' + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };

    }

    /**
     * An operation using the current database backend.
     */
    @FunctionalInterface
    private interface BackendFunction<T> {
        T apply(final @NotNull StorageBackend storage) throws Exception;
    }

    /**
     * A database worker operation that does not return a value.
     */
    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    /**
     * A database worker operation that returns a value.
     */
    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
