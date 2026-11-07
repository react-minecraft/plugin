package com.g4vrk.react.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.List;

@ConfigSerializable
public record ActionsConfig(
        Alerts alerts,
        Monitor monitor,
        Reload reload,
        Reboot reboot,
        Rcps rcps,
        Help help,
        Texts texts
) {

    /**
     * The actions of {@code /react alerts} and {@code /react monitor}. {@code onEnablePlayers} runs
     * when only some players are followed and gets their names in {@code <players>}.
     */
    public interface ReportToggle {

        List<String> onEnable();

        List<String> onEnablePlayers();

        List<String> onDisable();

    }

    @ConfigSerializable
    public record Alerts(
            List<String> alert,
            List<String> onEnable,
            List<String> onEnablePlayers,
            List<String> onDisable
    ) implements ReportToggle {
    }

    @ConfigSerializable
    public record Monitor(
            List<String> message,
            List<String> onEnable,
            List<String> onEnablePlayers,
            List<String> onDisable
    ) implements ReportToggle {
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
