package com.g4vrk.react.sponge.command;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.platform.ReactPlatform;
import io.leangen.geantyref.TypeToken;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Parses player names and Sponge-independent player selectors for shared Cloud commands.
 */
final class SpongePlayerArgumentParser {

    private final ReactPlatform platform;

    SpongePlayerArgumentParser(final @NotNull ReactPlatform platform) {

        this.platform = platform;

    }

    @NotNull ParserDescriptor<PlatformSender, PlatformPlayer> singlePlayer() {

        return ParserDescriptor.of(new ArgumentParser<>() {
            @Override
            public ArgumentParseResult<PlatformPlayer> parse(
                    final CommandContext<PlatformSender> context,
                    final CommandInput input
            ) {

                final String token = input.readString();
                final List<PlatformPlayer> players = resolve(context, token);
                return players.size() == 1
                        ? ArgumentParseResult.success(players.get(0))
                        : ArgumentParseResult.failure(new IllegalArgumentException("Expected exactly one online player"));

            }

            @Override
            public SuggestionProvider<PlatformSender> suggestionProvider() {

                return (context, input) -> SpongePlayerArgumentParser.this.suggestions(input);

            }

        }, PlatformPlayer.class);

    }

    @NotNull ParserDescriptor<PlatformSender, Collection<PlatformPlayer>> multiplePlayers() {

        return ParserDescriptor.of(new ArgumentParser<>() {
            @Override
            public ArgumentParseResult<Collection<PlatformPlayer>> parse(
                    final CommandContext<PlatformSender> context,
                    final CommandInput input
            ) {

                final String token = input.readString();
                final List<PlatformPlayer> players = resolve(context, token);
                return players.isEmpty()
                        ? ArgumentParseResult.failure(new IllegalArgumentException("Expected at least one online player"))
                        : ArgumentParseResult.success(players);

            }

            @Override
            public SuggestionProvider<PlatformSender> suggestionProvider() {

                return (context, input) -> SpongePlayerArgumentParser.this.suggestions(input);

            }

        }, new TypeToken<Collection<PlatformPlayer>>() {

        });

    }

    private List<PlatformPlayer> resolve(
            final @NotNull CommandContext<PlatformSender> context,
            final @NotNull String token
    ) {

        if (token.equals("@a")) {
            return List.copyOf(platform.onlinePlayers());
        }
        if (token.equals("@s")) {
            final PlatformPlayer player = context.sender().asPlayer();
            return player == null ? List.of() : List.of(player);
        }
        if (token.equals("@p")) {
            final PlatformPlayer source = context.sender().asPlayer();
            if (source == null) {
                return List.of();
            }
            final ServerPlayer sourcePlayer = source.unwrap(ServerPlayer.class);
            if (sourcePlayer == null) {
                return List.of();
            }
            PlatformPlayer nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (final PlatformPlayer online : platform.onlinePlayers()) {
                final ServerPlayer target = online.unwrap(ServerPlayer.class);
                if (target == null || !target.world().key().equals(sourcePlayer.world().key())) {
                    continue;
                }
                final double distance = target.position().distanceSquared(sourcePlayer.position());
                if (distance < nearestDistance) {
                    nearest = online;
                    nearestDistance = distance;
                }
            }
            return nearest == null ? List.of() : List.of(nearest);
        }
        if (token.equals("@r")) {
            final Collection<? extends PlatformPlayer> online = platform.onlinePlayers();
            if (online.isEmpty()) {
                return List.of();
            }
            final int selected = ThreadLocalRandom.current().nextInt(online.size());
            int index = 0;
            for (final PlatformPlayer player : online) {
                if (index++ == selected) {
                    return List.of(player);
                }
            }
            return List.of();
        }
        if (token.startsWith("@")) {
            return List.of();
        }
        final PlatformPlayer player = platform.player(token);
        return player == null ? List.of() : List.of(player);

    }

    private CompletableFuture<? extends Iterable<? extends Suggestion>> suggestions(
            final @NotNull CommandInput input
    ) {

        final String prefix = input.remainingInput().toLowerCase(Locale.ROOT);
        final List<Suggestion> suggestions = new ArrayList<>();
        for (final PlatformPlayer player : platform.onlinePlayers()) {
            if (player.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(Suggestion.suggestion(player.name()));
            }
        }
        for (final String selector : List.of("@a", "@p", "@r", "@s")) {
            if (selector.startsWith(prefix)) {
                suggestions.add(Suggestion.suggestion(selector));
            }
        }
        return CompletableFuture.completedFuture(suggestions);

    }

}
