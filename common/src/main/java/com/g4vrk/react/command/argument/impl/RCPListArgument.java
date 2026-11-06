package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.rcp.HostedEnvironment;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.rcp.update.RCPUpdater;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * {@code /react rcps}: every loaded RCP, and how the React RCP was last updated.
 */
public final class RCPListArgument extends LocalArgument {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String PREFIX = "<b><gradient:#eb2544:#ff3655>⚡ ʀᴇᴀᴄᴛ</gradient></b> <gray>»</gray> ";
    private static final String SEPARATOR = " <dark_gray>|</dark_gray> ";

    private final CommandBuilderFactory builderFactory;
    private final RCPHost host;
    private final RCPUpdater updater;

    public RCPListArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull RCPHost host,
            final @NotNull RCPUpdater updater
    ) {
        this.builderFactory = builderFactory;
        this.host = host;
        this.updater = updater;
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal("rcps")
                .permission("react.command.rcps")
                .handler(context -> {
                    final CommandSender sender = context.sender();
                    final List<? extends HostedEnvironment> environments = host.environments();

                    sender.sendMessage(line(PREFIX + "<white>RCP: <color:#eb2544>" + environments.size()));
                    for (final HostedEnvironment environment : environments) {
                        final RCPMetadata metadata = environment.metadata();
                        final String status = environment.currentStatus();
                        sender.sendMessage(line(" <dark_gray>•</dark_gray> <white>" + escape(metadata.name())
                                + " <gray>v" + escape(metadata.version())
                                + SEPARATOR + "api " + metadata.apiVersion()
                                + (status == null ? "" : SEPARATOR + "<color:#ff3655>" + escape(status))));
                    }

                    final RCPUpdater.Status react = updater.status();
                    sender.sendMessage(line(" <dark_gray>»</dark_gray> <white>React RCP <gray>"
                            + (react.version() == null ? "-" : "v" + escape(react.version()))
                            + SEPARATOR + react.channel().name().toLowerCase(Locale.ROOT)
                            + SEPARATOR + escape(react.state())
                            + SEPARATOR + ago(react.lastCheck())));
                });
    }

    private static @NotNull Component line(final @NotNull String text) {
        return MINI.deserialize(text);
    }

    private static @NotNull String escape(final @NotNull String text) {
        return MINI.escapeTags(text);
    }

    private static @NotNull String ago(final @Nullable Instant instant) {
        if (instant == null) {
            return "not checked";
        }
        final long minutes = Duration.between(instant, Instant.now()).toMinutes();
        return minutes < 1 ? "checked just now" : "checked " + minutes + " min ago";
    }

}
