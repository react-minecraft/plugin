package com.g4vrk.react.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.time.Duration;
import java.util.Map;

@ConfigSerializable
public record MainConfig(Player player, Map<String, OfficialAddon> officialAddons) {

    @ConfigSerializable
    public record Player(boolean checkBedrockPlayers, Combat combat) {
    }

    @ConfigSerializable
    public record Combat(Duration time) {
    }

    /**
     * An addon from the react-minecraft organization that React can download by itself.
     *
     * @param repository        the GitHub repository, {@code owner/name}
     * @param downloadNextStart whether a missing jar is downloaded on the next start
     */
    @ConfigSerializable
    public record OfficialAddon(String repository, boolean downloadNextStart) {
    }

}
