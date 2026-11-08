package com.g4vrk.react.history;

import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * The model verdicts kept for one player, oldest first.
 *
 * <p>Reads take the read lock only. Expired entries are dropped at most once a second, so a
 * command that reads the history ten times does not write-lock ten times. {@link #revision()}
 * grows with every change, which lets a caller cache a statistic over the whole history
 * instead of walking it again for each call.</p>
 */
public final class InferenceHistory {

    private static final long PRUNE_INTERVAL_MILLIS = 1_000L;

    private final List<InferenceHistoryEntry> entries = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final long retentionMillis;
    private final Consumer<InferenceHistoryEntry> entrySink;

    private volatile long revision;
    private volatile long lastPrune = System.currentTimeMillis();

    public InferenceHistory(final @NotNull Duration retention) {
        this(retention, ignored -> {
        });
    }

    public InferenceHistory(
            final @NotNull Duration retention,
            final @NotNull Consumer<InferenceHistoryEntry> entrySink
    ) {
        this.retentionMillis = retention.toMillis();
        this.entrySink = entrySink;
    }

    /**
     * Tells how many times the contents changed, so a caller can cache a value over them.
     *
     * @return a number that grows with every change
     */
    public long revision() {
        return this.revision;
    }

    public void add(
            final @NotNull InferenceHistoryEntry entry
    ) {
        this.lock.writeLock().lock();
        try {
            this.pruneUnsafe(System.currentTimeMillis() - this.retentionMillis);
            this.entries.add(entry);
            this.revision++;
        } finally {
            this.lock.writeLock().unlock();
        }
        this.entrySink.accept(entry);
    }

    public void replace(final @NotNull Collection<InferenceHistoryEntry> loaded) {
        this.lock.writeLock().lock();
        try {
            this.entries.clear();
            this.entries.addAll(loaded);
            this.entries.sort(Comparator.comparingLong(InferenceHistoryEntry::getTimestamp));
            this.pruneUnsafe(System.currentTimeMillis() - this.retentionMillis);
            this.revision++;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void merge(final @NotNull Collection<InferenceHistoryEntry> loaded) {
        this.lock.writeLock().lock();
        try {
            final Set<EntryIdentity> known = new HashSet<>(this.entries.size() + loaded.size());
            for (final InferenceHistoryEntry entry : this.entries) {
                known.add(EntryIdentity.of(entry));
            }
            for (final InferenceHistoryEntry entry : loaded) {
                if (known.add(EntryIdentity.of(entry))) {
                    this.entries.add(entry);
                }
            }
            this.entries.sort(Comparator.comparingLong(InferenceHistoryEntry::getTimestamp));
            this.pruneUnsafe(System.currentTimeMillis() - this.retentionMillis);
            this.revision++;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void pruneBefore(final long cutoff) {
        this.lock.writeLock().lock();
        try {
            this.pruneUnsafe(cutoff);
            this.lastPrune = System.currentTimeMillis();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public @Nullable InferenceHistoryEntry get(
            final int index
    ) {
        this.pruneExpired();
        this.lock.readLock().lock();
        try {
            return index < 0 || index >= this.entries.size() ? null : this.entries.get(index);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public @NotNull InferenceHistoryEntry[] entries() {
        this.pruneExpired();
        this.lock.readLock().lock();
        try {
            return this.entries.toArray(InferenceHistoryEntry[]::new);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Passes every probability to a consumer without copying the history.
     *
     * @param action what to do with each probability, oldest first
     */
    public void forEachProbability(final @NotNull DoubleConsumer action) {
        this.pruneExpired();
        this.lock.readLock().lock();
        try {
            for (int i = 0; i < this.entries.size(); i++) {
                action.accept(this.entries.get(i).getProbability());
            }
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public @Nullable InferenceHistoryEntry latest() {
        this.pruneExpired();
        this.lock.readLock().lock();
        try {
            return this.entries.isEmpty() ? null : this.entries.get(this.entries.size() - 1);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public int size() {
        this.pruneExpired();
        this.lock.readLock().lock();
        try {
            return this.entries.size();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public void clear() {
        this.lock.writeLock().lock();
        try {
            this.entries.clear();
            this.revision++;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private void pruneUnsafe(final long cutoff) {
        if (this.entries.removeIf(entry -> entry.getTimestamp() < cutoff)) {
            this.revision++;
        }
    }

    private void pruneExpired() {
        final long now = System.currentTimeMillis();

        if (now - this.lastPrune < PRUNE_INTERVAL_MILLIS) {
            return;
        }

        this.lock.writeLock().lock();
        try {
            if (now - this.lastPrune < PRUNE_INTERVAL_MILLIS) {
                return;
            }
            this.lastPrune = now;
            this.pruneUnsafe(now - this.retentionMillis);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private record EntryIdentity(long timestamp, String check, long probability) {
        private static @NotNull EntryIdentity of(final @NotNull InferenceHistoryEntry entry) {
            return new EntryIdentity(
                    entry.getTimestamp(),
                    entry.getCheck().id(),
                    Double.doubleToLongBits(entry.getProbability())
            );
        }
    }
}
