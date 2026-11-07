package com.g4vrk.react.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.List;

@ConfigSerializable
public record ActionsConfig(
        Toggle alerts,
        Toggle verbose,
        Reload reload,
        Reboot reboot,
        Rcps rcps,
        Help help,
        Texts texts
) {

    @ConfigSerializable
    public record Toggle(List<String> onEnable, List<String> onDisable) {
    }

    @ConfigSerializable
    public record Reload(List<String> onStart, List<String> onFinish) {
    }

    /**
     * {@code /react reboot}. The three texts go out while the plugin is unloaded, so they are plain
     * messages and not actions.
     */
    @ConfigSerializable
    public record Reboot(
            List<String> starting,
            List<String> cancelled,
            String unloaded,
            String loaded,
            String failed
    ) {
    }

    @ConfigSerializable
    public record Rcps(
            List<String> header,
            List<String> entry,
            List<String> entryWithStatus,
            List<String> react
    ) {
    }

    @ConfigSerializable
    public record Texts(
            String unavailable,
            String noVersion,
            String version,
            String notChecked,
            String checkedNow,
            String checkedAgo
    ) {
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
