package com.g4vrk.react.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.time.Duration;

@ConfigSerializable
public record MainConfig(Player player) {

    @ConfigSerializable
    public record Player(boolean checkBedrockPlayers, Combat combat) {
    }

    @ConfigSerializable
    public record Combat(Duration time) {
    }

}
