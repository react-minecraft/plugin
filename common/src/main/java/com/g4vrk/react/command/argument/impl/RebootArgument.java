package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.reboot.PluginReboot;
import com.g4vrk.text.LegacySerializers;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

/**
 * /react reboot: unloads React and loads it again from the jar in plugins/, so a replaced jar
 * or added and removed addons take effect without restarting the server.
 */
public final class RebootArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private final CommandBuilderFactory builderFactory;
    private final ActionParser<Audience> actionParser;

    private ExecutableActionList<? super Audience> starting;
    private ExecutableActionList<? super Audience> cancelled;
    private PluginReboot.Messages messages;

    public RebootArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull ActionsConfig config
    ) {
        this.builderFactory = builderFactory;
        this.actionParser = actionParser;
        this.onReload(config);
    }

    @Override
    public void onReload(final @NotNull ActionsConfig config) {
        final ActionsConfig.Reboot reboot = config.reboot();
        this.starting = actionParser.parseAll(reboot.starting());
        this.cancelled = actionParser.parseAll(reboot.cancelled());
        // Sent after the class loader closed: turned into plain legacy strings now.
        this.messages = new PluginReboot.Messages(legacy(reboot.unloaded()), legacy(reboot.loaded()), legacy(reboot.failed()));
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal("reboot")
                .permission("react.command.reboot")
                .handler(context -> {
                    final CommandSender sender = context.sender();
                    final Plugin plugin = React.INSTANCE.plugin();
                    final PluginReboot reboot;
                    try {
                        reboot = PluginReboot.prepare(plugin, sender, messages);
                    } catch (final IllegalStateException refused) {
                        React.INSTANCE.logger().warn("React reboot refused: {}", refused.getMessage());
                        final String reason = String.valueOf(refused.getMessage());
                        cancelled.run(sender, text -> text.replace("{reason}", reason));
                        return;
                    }
                    starting.run(sender, text -> text
                            .replace("{jar}", reboot.jar().getName())
                            .replace("{version}", reboot.newVersion()));
                    // Run outside the command dispatch: disabling React tears down this command manager.
                    Bukkit.getScheduler().runTask(plugin, reboot);
                });
    }

    private static String legacy(final String text) {
        return LegacyComponentSerializer.legacySection()
                .serialize(LegacySerializers.INSTANCE.universalSerializer().serialize(text));
    }

}
