package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.api.rcp.command.RCPCommand;
import com.g4vrk.react.command.HelpActions;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.rcp.RCPHost;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Routes {@code /react <name> [args]} to the subcommands RCPs register.
 *
 * <p>The command tree is built once at startup, so RCP commands are looked up by name at
 * dispatch time instead of being added to the tree. The plugin's own literals win over
 * this argument.</p>
 */
public final class RCPCommandArgument extends LocalArgument {

    private final CommandBuilderFactory builderFactory;
    private final RCPHost host;
    private final HelpActions help;

    public RCPCommandArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull RCPHost host,
            final @NotNull HelpActions help
    ) {
        this.builderFactory = builderFactory;
        this.host = host;
        this.help = help;
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .required("name", StringParser.stringParser(), SuggestionProvider.blocking((context, input) ->
                        host.commands().stream()
                                .filter(command -> context.sender().hasPermission(command.permission()))
                                .map(command -> Suggestion.suggestion(command.name()))
                                .toList()))
                .optional("args", StringParser.greedyStringParser(), SuggestionProvider.blocking((context, input) -> {
                    final RCPCommand command = host.command(context.get("name"));
                    if (command == null || !context.sender().hasPermission(command.permission())) {
                        return List.of();
                    }
                    return command.suggest(context.sender(), split(input.remainingInput()))
                            .stream().map(Suggestion::suggestion).toList();
                }))
                .handler(context -> {
                    final CommandSender sender = context.sender();
                    final RCPCommand command = host.command(context.get("name"));
                    if (command == null) {
                        help.unknownCommand(sender);
                        return;
                    }
                    if (!sender.hasPermission(command.permission())) {
                        help.noPermission(sender);
                        return;
                    }
                    command.execute(sender, split(context.getOrDefault("args", "")));
                });
    }

    private static @NotNull List<String> split(final @NotNull String input) {
        final String trimmed = input.strip();
        return trimmed.isEmpty() ? List.of() : Arrays.asList(trimmed.split("\\s+"));
    }

}
