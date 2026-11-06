package com.g4vrk.react.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record AimAIConfig(boolean debug, Alert alert, Decay decay) {

    @ConfigSerializable
    public record Alert(double threshold) {
    }

    @ConfigSerializable
    public record Decay(double amount) {
    }

}
