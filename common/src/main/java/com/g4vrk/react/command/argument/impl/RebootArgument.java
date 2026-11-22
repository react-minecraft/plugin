package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.platform.reboot.PluginRebootMessage;
import com.g4vrk.react.platform.reboot.PreparedPluginReboot;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.text.LegacySerializerRegistry;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.incendo.cloud.Command;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /react reboot}: reboots React (when supported by the active platform) or extensions.
 */
public final class RebootArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private static final CloudKey<String> TARGET = CloudKey.of("target", String.class);

    private final CommandBuilderFactory builderFactory;
    private final ReactPlatform platform;
    private final ActionParser<Audience> actionParser;
    private final ExtensionManager extensions;
    private final TaskScheduler<PlatformPlayer> scheduler;

    private volatile ExecutableActionList<? super Audience> starting;
    private volatile ExecutableActionList<? super Audience> cancelled;
    private volatile ExecutableActionList<? super Audience> extensionsStarting;
    private volatile ExecutableActionList<? super Audience> extensionsDone;
    private volatile ExecutableActionList<? super Audience> extensionsUnknown;
    private volatile PluginRebootMessage messages;

    public RebootArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull ReactPlatform platform,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull ActionsConfig config,
            final @NotNull ExtensionManager extensions,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    ) {

        this.builderFactory = builderFactory;
        this.platform = platform;
        this.actionParser = actionParser;
        this.extensions = extensions;
        this.scheduler = scheduler;
        this.onReload(config);

    }

    @Override
    public void onReload(
            final @NotNull ActionsConfig config
    ) {

        final ActionsConfig.Reboot reboot = config.reboot();
        this.starting = actionParser.parseAll(reboot.starting());
        this.cancelled = actionParser.parseAll(reboot.cancelled());
        this.extensionsStarting = actionParser.parseAll(reboot.extensionsStarting());
        this.extensionsDone = actionParser.parseAll(reboot.extensionsDone());
        this.extensionsUnknown = actionParser.parseAll(reboot.extensionsUnknown());
        /*
         * Sent after the class loader closed: turned into plain legacy strings now.
         */
        this.messages = new PluginRebootMessage(
                legacy(reboot.unloaded()),
                legacy(reboot.loaded()),
                legacy(reboot.failed())
        );

    }

    @Override
    public @NotNull Command.Builder<PlatformSender> build() {

        return builderFactory.create()
                .literal("reboot")
                .permission("react.command.reboot")
                .optional(
                        TARGET,
                        StringParser.stringParser(),
                        SuggestionProvider.blocking((context, input) ->
                                extensions.targets().stream().map(Suggestion::suggestion).toList())
                )
                .handler(context -> {
                    final PlatformSender sender = context.sender();
                    final String target = context.getOrDefault(TARGET, null);
                    if (target != null) {
                        rebootExtensions(sender, target);
                        return;
                    }
                    final PreparedPluginReboot reboot;
                    try {
                        reboot = platform.preparePluginReboot(sender, messages);
                    } catch (final IllegalStateException refused) {
                        React.INSTANCE.logger().warn("React reboot refused: {}", refused.getMessage());
                        final String reason = String.valueOf(refused.getMessage());
                        cancelled.run(sender, text -> text.replace("<reason>", reason));
                        return;
                    }
                    starting.run(
                            sender,
                            text -> text
                                    .replace("<jar>", reboot.artifactName())
                                    .replace("<version>", reboot.newVersion())
                    );
                    reboot.schedule();
                });

    }

    private void rebootExtensions(
            final @NotNull PlatformSender sender,
            final @NotNull String target
    ) {

        extensionsStarting.run(sender, text -> text.replace("<target>", target));
        extensions.reboot(target).whenComplete((result, failure) -> {
            scheduler.schedule(() -> {
                if (failure != null) {
                    React.INSTANCE.logger().error("Could not reboot '{}'", target, failure);
                    return;
                }
                if (result.empty()) {
                    extensionsUnknown.run(sender, text -> text.replace("<target>", target));
                    return;
                }
                extensionsDone.run(
                        sender,
                        text -> text
                                .replace("<loaded>", names(result.loaded()))
                                .replace("<unloaded>", names(result.unloaded()))
                                .replace("<failed>", names(result.failed()))
                );
            }, TickSchedule.instant());
        });

    }

    private static @NotNull String names(
            final @NotNull List<String> names
    ) {

        return names.isEmpty() ? "-" : String.join(", ", names);

    }

    private static String legacy(
            final String text
    ) {

        return LegacyComponentSerializer.legacySection()
                .serialize(LegacySerializerRegistry.INSTANCE.universalSerializer().serialize(text));

    }

}
