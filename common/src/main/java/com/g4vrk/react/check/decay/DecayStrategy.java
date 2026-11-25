package com.g4vrk.react.check.decay;

/**
 * Computes decayed check violation levels over elapsed ticks or decay intervals.
 */
public interface DecayStrategy {

    double decay(final double violations);

}
