package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.Permissions;
import com.g4vrk.react.api.channel.ReactChannels;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

public final class MonitorArgument extends ReportToggleArgument {

    public MonitorArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull ActionParser<Audience> actionParser
    ) {
        super(builderFactory, actionParser, ReactChannels.MONITOR, Permissions.MONITOR, ActionsConfig::monitor,
                "monitor", "verbose");
    }

}
