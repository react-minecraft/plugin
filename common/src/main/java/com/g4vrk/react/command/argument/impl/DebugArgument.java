package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.Permissions;
import com.g4vrk.react.api.channel.BuiltinChannels;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /react debug [players] [check]}: the debug lines of the checks.
 *
 * <p>Each check shows them with the {@code debug} actions of its own config.</p>
 */
public final class DebugArgument extends ReportToggleArgument {

    public DebugArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull ActionParser<Audience> actionParser
    ) {
        super(
                builderFactory,
                actionParser,
                BuiltinChannels.DEBUG,
                Permissions.DEBUG,
                ActionsConfig::debug,
                true,
                "debug"
        );
    }

}
