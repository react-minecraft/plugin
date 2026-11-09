package com.g4vrk.react.statistic;

import com.g4vrk.react.history.InferenceHistory;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleConsumer;

/**
 * Average, highest and lowest probability of one player's verdicts.
 *
 * <p>The three values are computed once per change of the history and then answered from one
 * small record, so a scoreboard line that asks for all three costs one pass over the history
 * rather than three, and no array copy. The accumulator and the consumer are fields, so an
 * unchanged history allocates nothing at all.</p>
 */
public final class InferenceStatistic {

    private final InferenceHistory history;

    private final double[] accumulator = new double[4];
    private final DoubleConsumer collector = this::accept;

    private long cachedRevision = Long.MIN_VALUE;
    private Result cached = Result.EMPTY;

    public InferenceStatistic(final @NotNull InferenceHistory history) {
        this.history = history;
    }

    public int size() {
        return this.history.size();
    }

    public double averageProbability() {
        return this.calculate().averageProbability();
    }

    public double maxProbability() {
        return this.calculate().maxProbability();
    }

    public double minProbability() {
        return this.calculate().minProbability();
    }

    public @Nullable InferenceHistoryEntry latest() {
        return this.history.latest();
    }

    /**
     * Computes the three values, or answers the ones of the last run when the history is unchanged.
     *
     * @return the statistic of the whole history
     */
    public synchronized @NotNull Result calculate() {
        final long revision = this.history.revision();

        if (revision == this.cachedRevision) {
            return this.cached;
        }

        this.accumulator[0] = 0.0D;
        this.accumulator[1] = Double.NEGATIVE_INFINITY;
        this.accumulator[2] = Double.POSITIVE_INFINITY;
        this.accumulator[3] = 0.0D;

        this.history.forEachProbability(this.collector);

        final Result result = this.accumulator[1] == Double.NEGATIVE_INFINITY
                ? Result.EMPTY
                : new Result(
                        this.accumulator[0] / this.accumulator[3],
                        this.accumulator[1],
                        this.accumulator[2]
                );

        this.cached = result;
        this.cachedRevision = revision;

        return result;
    }

    private void accept(final double probability) {
        this.accumulator[0] += probability;
        this.accumulator[3]++;

        if (probability > this.accumulator[1]) {
            this.accumulator[1] = probability;
        }

        if (probability < this.accumulator[2]) {
            this.accumulator[2] = probability;
        }
    }

    /**
     * The three values of one history.
     *
     * @param averageProbability the mean of every verdict
     * @param maxProbability     the highest verdict
     * @param minProbability     the lowest verdict
     */
    public record Result(double averageProbability, double maxProbability, double minProbability) {

        private static final Result EMPTY = new Result(0.0D, 0.0D, 0.0D);

    }

}
