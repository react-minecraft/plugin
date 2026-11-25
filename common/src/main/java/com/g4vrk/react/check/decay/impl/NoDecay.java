package com.g4vrk.react.check.decay.impl;

import com.g4vrk.react.check.decay.DecayStrategy;

/**
 * Identity {@link DecayStrategy} that preserves the current violation level unchanged.
 */
public final class NoDecay implements DecayStrategy {

    public static final NoDecay INSTANCE = new NoDecay();

    private NoDecay() {

    }

    @Override
    public double decay(
            final double violations
    ) {

        return violations;

    }

}
