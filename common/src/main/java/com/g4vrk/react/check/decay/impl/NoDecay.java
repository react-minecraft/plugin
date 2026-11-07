package com.g4vrk.react.check.decay.impl;

import com.g4vrk.react.check.decay.DecayStrategy;

public final class NoDecay implements DecayStrategy {

    @Override
    public double decay(
            final double violations
    ) {
        return violations;
    }

}
