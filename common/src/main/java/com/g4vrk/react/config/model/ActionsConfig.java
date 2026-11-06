package com.g4vrk.react.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.List;

@ConfigSerializable
public record ActionsConfig(Toggle alerts, Toggle verbose, Reload reload, Help help) {

    @ConfigSerializable
    public record Toggle(List<String> onEnable, List<String> onDisable) {
    }

    @ConfigSerializable
    public record Reload(List<String> onStart, List<String> onFinish) {
    }

    @ConfigSerializable
    public record Help(
            List<String> root,
            List<String> noPermission,
            List<String> unknownCommand,
            List<String> playerOnly
    ) {
    }

}
