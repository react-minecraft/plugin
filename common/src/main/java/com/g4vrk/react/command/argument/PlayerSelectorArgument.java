package com.g4vrk.react.command.argument;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Cloud's native player selector restricted to player-targeting selector tokens.
 */
public final class PlayerSelectorArgument {

    public static final CloudKey<MultiplePlayerSelector> PLAYERS = CloudKey.of("players", MultiplePlayerSelector.class);

    private PlayerSelectorArgument() {

    }

    public static CommandComponent.@NotNull Builder<CommandSender, MultiplePlayerSelector> players(final boolean required) {

        final ParserDescriptor<CommandSender, MultiplePlayerSelector> parser = MultiplePlayerSelectorParser.multiplePlayerSelectorParser(false);
        return CommandComponent.<CommandSender, MultiplePlayerSelector>builder()
                .key(PLAYERS)
                .parser(parser)
                .required(required)
                .preprocessor((context, input) -> allowed(input.remainingInput().stripLeading())
                        ? ArgumentParseResult.success(true)
                        : ArgumentParseResult.failure(new IllegalArgumentException("Expected a player selector")))
                .suggestionProvider((context, input) -> parser.parser().suggestionProvider().suggestionsFuture(context, input)
                        .thenApply(suggestions -> {
                            final List<Suggestion> players = new ArrayList<>();
                            for (final Suggestion suggestion : suggestions) {
                                if (allowed(suggestion.suggestion())) {
                                    players.add(suggestion);
                                }
                            }
                            return players;
                        }));

    }

    private static boolean allowed(final @NotNull String token) {

        return token.length() < 2 || token.charAt(0) != '@' || "aprs".indexOf(token.charAt(1)) >= 0;

    }

}
