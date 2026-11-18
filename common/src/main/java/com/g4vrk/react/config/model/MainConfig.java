package com.g4vrk.react.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.time.Duration;
import java.util.Map;

@ConfigSerializable
public record MainConfig(Player player, Map<String, ExternalAddon> externalAddons) {

    @ConfigSerializable
    public record Player(boolean checkBedrockPlayers, Combat combat) {
    }

    @ConfigSerializable
    public record Combat(Duration time) {
    }

    /**
     * An addon that React can download from a signed GitHub release.
     *
     * @param repository        the GitHub repository, {@code owner/name}
     * @param downloadNextStart whether a missing jar is downloaded on the next start
     */
    @ConfigSerializable
    public record ExternalAddon(String repository, boolean downloadNextStart) {
    }

}
