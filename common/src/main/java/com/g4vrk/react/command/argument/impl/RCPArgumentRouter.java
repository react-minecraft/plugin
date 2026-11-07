package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.command.HelpActions;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.extension.ArgumentTable;
import com.g4vrk.react.extension.RegisteredArgument;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Routes {@code /react <name> [args]} to the arguments RCPs and addons register.
 *
 * <p>The command tree is built once at startup, so extension arguments are looked up by name at
 * dispatch time instead of being added to the tree. The plugin's own literals win over
 * this argument.</p>
 */
public final class RCPArgumentRouter extends LocalArgument {

    private final CommandBuilderFactory builderFactory;
    private final ArgumentTable arguments;
    private final HelpActions help;

    public RCPArgumentRouter(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull ArgumentTable arguments,
            final @NotNull HelpActions help
    ) {
        this.builderFactory = builderFactory;
        this.arguments = arguments;
        this.help = help;
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .required("name", StringParser.stringParser(), SuggestionProvider.blocking((context, input) ->
                        arguments.all().stream()
                                .filter(argument -> context.sender().hasPermission(argument.permission()))
                                .map(argument -> Suggestion.suggestion(argument.name()))
                                .toList()))
                .optional("args", StringParser.greedyStringParser(), SuggestionProvider.blocking((context, input) -> {
                    final RegisteredArgument argument = arguments.get(context.get("name"));
                    if (argument == null || !context.sender().hasPermission(argument.permission())) {
                        return List.of();
                    }
                    return argument.argument().suggest(context.sender(), split(input.remainingInput()))
                            .stream().map(Suggestion::suggestion).toList();
                }))
                .handler(context -> {
                    final CommandSender sender = context.sender();
                    final RegisteredArgument argument = arguments.get(context.get("name"));
                    if (argument == null) {
                        help.unknownCommand(sender);
                        return;
                    }
                    if (!sender.hasPermission(argument.permission())) {
                        help.noPermission(sender);
                        return;
                    }
                    argument.argument().execute(sender, split(context.getOrDefault("args", "")));
                });
    }

    private static @NotNull List<String> split(final @NotNull String input) {
        final String trimmed = input.strip();
        return trimmed.isEmpty() ? List.of() : Arrays.asList(trimmed.split("\\s+"));
    }

}
