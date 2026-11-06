package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.Permissions;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.channel.ReactChannels;
import com.g4vrk.react.api.channel.impl.ChatMessageChannel;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class VerboseArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private final ChatMessageChannel verboseChannel;

    private final CommandBuilderFactory builderFactory;

    private final ActionParser<Audience> actionParser;

    private ExecutableActionList<? super Audience> enabledActions;
    private ExecutableActionList<? super Audience> disabledActions;

    public VerboseArgument(
            @NotNull CommandBuilderFactory builderFactory,
            @NotNull ActionParser<Audience> actionParser
    ) {
        this.verboseChannel = ReactChannels.VERBOSE;

        this.builderFactory = builderFactory;
        this.actionParser = actionParser;

        this.reload();
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return Objects.requireNonNull(builderFactory)
                .create()
                .literal("verbose")
                .permission(Permissions.VERBOSE)
                .handler(context -> {

                    final CommandSender sender = context.sender();

                    if (!this.verboseChannel.remove(sender)) {

                        this.verboseChannel.subscribe(sender);

                        if (this.enabledActions != null) this.enabledActions.run(sender);

                    } else {

                        if (this.disabledActions != null) this.disabledActions.run(sender);

                    }
                });
    }

    public void reload() {
        this.onReload(React.INSTANCE.getConfigs().actions());
    }

    @Override
    public void onReload(@NotNull ActionsConfig config) {
        this.enabledActions = this.actionParser.parseAll(config.verbose().onEnable());
        this.disabledActions = this.actionParser.parseAll(config.verbose().onDisable());
    }

}
