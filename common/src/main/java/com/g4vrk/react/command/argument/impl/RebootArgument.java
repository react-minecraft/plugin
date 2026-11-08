package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.reboot.PluginReboot;
import com.g4vrk.text.NativeSerializerRegistry;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.Command;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /react reboot}: unloads React and loads it again from the jar in plugins/, so a
 * replaced jar takes effect without restarting the server.
 *
 * <p>{@code /react reboot <target>} reboots only extensions: {@code all}, {@code rcp},
 * {@code addons} or one name. Their staged updates are put in place on the way.</p>
 */
public final class RebootArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private final CommandBuilderFactory builderFactory;
    private final ActionParser<Audience> actionParser;
    private final ExtensionManager extensions;

    private ExecutableActionList<? super Audience> starting;
    private ExecutableActionList<? super Audience> cancelled;
    private ExecutableActionList<? super Audience> extensionsStarting;
    private ExecutableActionList<? super Audience> extensionsDone;
    private ExecutableActionList<? super Audience> extensionsUnknown;
    private PluginReboot.Messages messages;

    public RebootArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull ActionsConfig config,
            final @NotNull ExtensionManager extensions
    ) {
        this.builderFactory = builderFactory;
        this.actionParser = actionParser;
        this.extensions = extensions;
        this.onReload(config);
    }

    @Override
    public void onReload(final @NotNull ActionsConfig config) {
        final ActionsConfig.Reboot reboot = config.reboot();
        this.starting = actionParser.parseAll(reboot.starting());
        this.cancelled = actionParser.parseAll(reboot.cancelled());
        this.extensionsStarting = actionParser.parseAll(reboot.extensionsStarting());
        this.extensionsDone = actionParser.parseAll(reboot.extensionsDone());
        this.extensionsUnknown = actionParser.parseAll(reboot.extensionsUnknown());
        // Sent after the class loader closed: turned into plain legacy strings now.
        this.messages = new PluginReboot.Messages(legacy(reboot.unloaded()), legacy(reboot.loaded()), legacy(reboot.failed()));
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal("reboot")
                .permission("react.command.reboot")
                .optional("target", StringParser.stringParser(), SuggestionProvider.blocking((context, input) ->
                        extensions.targets().stream().map(Suggestion::suggestion).toList()))
                .handler(context -> {
                    final CommandSender sender = context.sender();
                    final String target = context.getOrDefault("target", null);
                    if (target != null) {
                        rebootExtensions(sender, target);
                        return;
                    }
                    final Plugin plugin = React.INSTANCE.plugin();
                    final PluginReboot reboot;
                    try {
                        reboot = PluginReboot.prepare(plugin, sender, messages);
                    } catch (final IllegalStateException refused) {
                        React.INSTANCE.logger().warn("React reboot refused: {}", refused.getMessage());
                        final String reason = String.valueOf(refused.getMessage());
                        cancelled.run(sender, text -> text.replace("<reason>", reason));
                        return;
                    }
                    starting.run(sender, text -> text
                            .replace("<jar>", reboot.jar().getName())
                            .replace("<version>", reboot.newVersion()));
                    // Run outside the command dispatch: disabling React tears down this command manager.
                    Bukkit.getScheduler().runTask(plugin, reboot);
                });
    }

    private void rebootExtensions(final @NotNull CommandSender sender, final @NotNull String target) {
        extensionsStarting.run(sender, text -> text.replace("<target>", target));
        extensions.reboot(target).whenComplete((result, failure) -> {
            if (failure != null) {
                React.INSTANCE.logger().error("Could not reboot '{}'", target, failure);
                return;
            }
            if (result.empty()) {
                extensionsUnknown.run(sender, text -> text.replace("<target>", target));
                return;
            }
            extensionsDone.run(sender, text -> text
                    .replace("<loaded>", names(result.loaded()))
                    .replace("<unloaded>", names(result.unloaded()))
                    .replace("<failed>", names(result.failed())));
        });
    }

    private static @NotNull String names(final @NotNull List<String> names) {
        return names.isEmpty() ? "-" : String.join(", ", names);
    }

    private static String legacy(final String text) {
        return LegacyComponentSerializer.legacySection()
                .serialize(NativeSerializerRegistry.INSTANCE.universalSerializer().serialize(text));
    }

}
