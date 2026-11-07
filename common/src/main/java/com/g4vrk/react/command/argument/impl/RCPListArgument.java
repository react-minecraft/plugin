package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.rcp.HostedEnvironment;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.rcp.update.RCPUpdater;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /react rcps}: every loaded RCP, and how the React RCP was last updated.
 */
public final class RCPListArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private final CommandBuilderFactory builderFactory;
    private final ActionParser<Audience> actionParser;
    private final RCPHost host;
    private final RCPUpdater updater;

    private ExecutableActionList<? super Audience> header;
    private ExecutableActionList<? super Audience> entry;
    private ExecutableActionList<? super Audience> entryWithStatus;
    private ExecutableActionList<? super Audience> react;
    private ActionsConfig.Texts texts;

    public RCPListArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull ActionsConfig config,
            final @NotNull RCPHost host,
            final @NotNull RCPUpdater updater
    ) {
        this.builderFactory = builderFactory;
        this.actionParser = actionParser;
        this.host = host;
        this.updater = updater;
        this.onReload(config);
    }

    @Override
    public void onReload(final @NotNull ActionsConfig config) {
        final ActionsConfig.Rcps rcps = config.rcps();
        this.header = actionParser.parseAll(rcps.header());
        this.entry = actionParser.parseAll(rcps.entry());
        this.entryWithStatus = actionParser.parseAll(rcps.entryWithStatus());
        this.react = actionParser.parseAll(rcps.react());
        this.texts = config.texts();
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal("rcps")
                .permission("react.command.rcps")
                .handler(context -> {
                    final CommandSender sender = context.sender();
                    final List<? extends HostedEnvironment> environments = host.environments();

                    run(header, sender, Map.of("<count>", Integer.toString(environments.size())));
                    for (final HostedEnvironment environment : environments) {
                        final RCPMetadata metadata = environment.metadata();
                        final String status = environment.currentStatus();
                        run(status == null ? entry : entryWithStatus, sender, Map.of(
                                "<name>", metadata.name(),
                                "<version>", metadata.version(),
                                "<api>", Integer.toString(metadata.apiVersion()),
                                "<status>", status == null ? "" : status));
                    }

                    final RCPUpdater.Status status = updater.status();
                    run(react, sender, Map.of(
                            "<version>", status.version() == null
                                    ? texts.noVersion()
                                    : texts.version().replace("<version>", status.version()),
                            "<channel>", status.channel().name().toLowerCase(Locale.ROOT),
                            "<state>", status.state(),
                            "<checked>", checked(status.lastCheck())));
                });
    }

    private @NotNull String checked(final @Nullable Instant instant) {
        if (instant == null) {
            return texts.notChecked();
        }
        final long minutes = Duration.between(instant, Instant.now()).toMinutes();
        return minutes < 1 ? texts.checkedNow() : texts.checkedAgo().replace("<minutes>", Long.toString(minutes));
    }

    private static void run(
            final ExecutableActionList<? super Audience> actions,
            final CommandSender sender,
            final Map<String, String> placeholders
    ) {
        actions.run(sender, text -> {
            String replaced = text;
            for (final Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                replaced = replaced.replace(placeholder.getKey(), placeholder.getValue());
            }
            return replaced;
        });
    }

}
