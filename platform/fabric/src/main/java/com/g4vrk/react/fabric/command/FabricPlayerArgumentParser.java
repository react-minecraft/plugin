package com.g4vrk.react.fabric.command;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.fabric.FabricPlatform;
import com.g4vrk.react.fabric.sender.FabricPlatformPlayer;
import com.g4vrk.react.fabric.sender.FabricPlatformSender;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.leangen.geantyref.TypeToken;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Parses and suggests Fabric single-player and multi-player Cloud command arguments.
 */
final class FabricPlayerArgumentParser {

    private static final List<String> SELECTORS = List.of("@a", "@p", "@r", "@s");

    private final FabricPlatform platform;

    FabricPlayerArgumentParser(
            final @NotNull FabricPlatform platform
    ) {

        this.platform = platform;

    }

    @NotNull ParserDescriptor<PlatformSender, PlatformPlayer> singlePlayer() {

        final ParserDescriptor<PlatformSender, String> parser = StringParser.<PlatformSender>stringParser();
        return parser.mapSuccess(
                PlatformPlayer.class,
                (context, input) -> CompletableFuture.completedFuture(resolveSingle(context.sender(), input))
        );

    }

    @NotNull ParserDescriptor<PlatformSender, Collection<PlatformPlayer>> multiplePlayers() {

        final ParserDescriptor<PlatformSender, String> parser = StringParser.<PlatformSender>stringParser();
        return parser.mapSuccess(
                new TypeToken<Collection<PlatformPlayer>>() {
                },
                (context, input) -> CompletableFuture.completedFuture(resolvePlayers(context.sender(), input))
        );

    }

    @NotNull CompletableFuture<? extends Iterable<? extends Suggestion>> suggest(
            final @NotNull String input
    ) {

        final String prefix = input.toLowerCase(Locale.ROOT);
        final List<Suggestion> suggestions = new ArrayList<>();
        for (final PlatformPlayer player : platform.onlinePlayers()) {
            if (player.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(Suggestion.suggestion(player.name()));
            }
        }
        for (final String selector : SELECTORS) {
            if (selector.startsWith(prefix)) {
                suggestions.add(Suggestion.suggestion(selector));
            }
        }
        return CompletableFuture.completedFuture(suggestions);

    }

    private @NotNull PlatformPlayer resolveSingle(
            final @NotNull PlatformSender sender,
            final @NotNull String input
    ) {

        final Collection<PlatformPlayer> players = resolvePlayers(sender, input);
        if (players.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one online player");
        }
        return players.iterator().next();

    }

    private @NotNull Collection<PlatformPlayer> resolvePlayers(
            final @NotNull PlatformSender sender,
            final @NotNull String input
    ) {

        final CommandSourceStack source;
        if (sender instanceof final FabricPlatformSender fabricSender) {
            source = fabricSender.commandSource();
        } else {
            throw new IllegalArgumentException("Player selectors require a Fabric command source");
        }
        try {
            final EntitySelector selector = EntityArgument.players().parse(new StringReader(input));
            final List<ServerPlayer> selected = selector.findPlayers(source);
            final List<PlatformPlayer> players = new ArrayList<>(selected.size());
            for (final ServerPlayer player : selected) {
                players.add(new FabricPlatformPlayer(player));
            }
            if (players.isEmpty()) {
                throw new IllegalArgumentException("Expected at least one online player");
            }
            return players;
        } catch (final CommandSyntaxException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }

    }

}
