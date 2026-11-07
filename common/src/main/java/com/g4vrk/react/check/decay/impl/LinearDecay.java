package com.g4vrk.react.check.decay.impl;

import com.g4vrk.react.check.decay.DecayStrategy;

public final class LinearDecay implements DecayStrategy {

    private final double amount;

    public LinearDecay(
            final double amount
    ) {
        this.amount = amount;
    }

    @Override
    public double decay(final double violations) {
        return Math.max(0.0, violations - amount);
    }

}
