package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.PermissionKey;
import com.g4vrk.react.api.channel.BuiltinChannelRegistry;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

public final class MonitorArgument extends ReportToggleArgument {

    public MonitorArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull PlatformCommandBridge platformCommands,
            final @NotNull ActionParser<Audience> actionParser
    ) {

        super(
                builderFactory,
                platformCommands,
                actionParser,
                BuiltinChannelRegistry.MONITOR,
                PermissionKey.MONITOR,
                ActionsConfig::monitor,
                false,
                "monitor",
                "verbose"
        );

    }

}
