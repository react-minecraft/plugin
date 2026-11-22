package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.PermissionKey;
import com.g4vrk.react.api.channel.BuiltinChannelRegistry;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /react debug [players] [check]}: the debug lines of the checks.
 */
public final class DebugArgument extends ReportToggleArgument {

    public DebugArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull PlatformCommandBridge platformCommands,
            final @NotNull ActionParser<Audience> actionParser
    ) {

        super(
                builderFactory,
                platformCommands,
                actionParser,
                BuiltinChannelRegistry.DEBUG,
                PermissionKey.DEBUG,
                ActionsConfig::debug,
                true,
                "debug"
        );

    }

}
