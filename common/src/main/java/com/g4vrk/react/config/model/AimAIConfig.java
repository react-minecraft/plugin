package com.g4vrk.react.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.List;

@ConfigSerializable
public record AimAIConfig(List<String> debug, Alert alert, Decay decay) implements CheckConfig {

    @ConfigSerializable
    public record Alert(double threshold) {
    }

    @ConfigSerializable
    public record Decay(double amount) {
    }

}
