package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.Permissions;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.storage.constant.StorageConstants;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.standard.DurationParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.incendo.cloud.type.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code /react bypass}: temporary bypasses, stored in the database.
 *
 * <p>{@code /react bypass <players> [duration|forever] [check]} grants one, e.g.
 * {@code /react bypass Steve 1h30m aim-ai}. A check id in place of the duration means forever.
 * {@code /react bypass remove <players> [check]} lifts it and {@code /react bypass list} shows
 * the online players that have one. The {@code react.bypass.<check>} permission is a lasting
 * bypass this command does not touch.</p>
 */
public final class BypassArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private static final String FOREVER = "forever";

    private final CommandBuilderFactory builderFactory;
    private final ActionParser<Audience> actionParser;
    private final PlayerRegistry playerRegistry;

    private ExecutableActionList<? super Audience> granted;
    private ExecutableActionList<? super Audience> removed;
    private ExecutableActionList<? super Audience> cancelled;
    private ExecutableActionList<? super Audience> listHeader;
    private ExecutableActionList<? super Audience> listEntry;
    private ExecutableActionList<? super Audience> listEmpty;
    private String allChecks;
    private String forever;

    public BypassArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull ActionsConfig config,
            final @NotNull PlayerRegistry playerRegistry
    ) {
        this.builderFactory = builderFactory;
        this.actionParser = actionParser;
        this.playerRegistry = playerRegistry;
        this.onReload(config);
    }

    @Override
    public void onReload(final @NotNull ActionsConfig config) {
        final ActionsConfig.Bypass bypass = config.bypass();
        this.granted = actionParser.parseAll(bypass.granted());
        this.removed = actionParser.parseAll(bypass.removed());
        this.cancelled = actionParser.parseAll(bypass.cancelled());
        this.listHeader = actionParser.parseAll(bypass.listHeader());
        this.listEntry = actionParser.parseAll(bypass.listEntry());
        this.listEmpty = actionParser.parseAll(bypass.listEmpty());
        this.allChecks = bypass.allChecks();
        this.forever = bypass.forever();
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return root()
                .required("players", MultiplePlayerSelectorParser.multiplePlayerSelectorParser(false))
                .optional("duration", ArgumentParser.firstOf(DurationParser.durationParser(), StringParser.stringParser()),
                        SuggestionProvider.blocking((context, input) -> Stream.concat(
                                Stream.of(FOREVER, "10m", "1h", "1d"),
                                checkIds().stream()).map(Suggestion::suggestion).toList()))
                .optional("check", StringParser.stringParser(), checkSuggestions())
                .handler(context -> {
                    final MultiplePlayerSelector selector = context.get("players");
                    final Either<Duration, String> time = context.getOrDefault("duration", null);
                    String check = context.getOrDefault("check", null);
                    Duration duration = null;
                    if (time != null) {
                        duration = time.primary().orElse(null);
                        final String text = time.fallback().orElse(null);
                        if (text != null && !text.equalsIgnoreCase(FOREVER) && check == null) {
                            check = text;
                        }
                    }
                    grant(context.sender(), selector.values(), duration, check);
                });
    }

    @Override
    public @NotNull List<Command.Builder<CommandSender>> builds() {
        return List.of(
                build(),
                root()
                        .literal("remove")
                        .required("players", MultiplePlayerSelectorParser.multiplePlayerSelectorParser(false))
                        .optional("check", StringParser.stringParser(), checkSuggestions())
                        .handler(context -> {
                            final MultiplePlayerSelector selector = context.get("players");
                            remove(context.sender(), selector.values(), context.getOrDefault("check", null));
                        }),
                root()
                        .literal("list")
                        .handler(context -> list(context.sender()))
        );
    }

    private @NotNull Command.Builder<CommandSender> root() {
        return builderFactory.create()
                .literal("bypass")
                .permission(Permissions.BYPASS_COMMAND);
    }

    private void grant(
            final @NotNull CommandSender sender,
            final @NotNull Collection<Player> players,
            final @Nullable Duration duration,
            final @Nullable String check
    ) {
        final Instant until = duration == null ? null : Instant.now().plus(duration);
        final List<String> names = new ArrayList<>(players.size());
        for (final Player player : players) {
            final TrackedPlayerImpl tracked = playerRegistry.get(player.getUniqueId());
            if (tracked != null && tracked.bypass(check, until)) {
                names.add(player.getName());
            } else {
                cancelled.run(sender, text -> text.replace("<player>", player.getName()));
            }
        }
        if (names.isEmpty()) {
            return;
        }
        final String joined = String.join(", ", names);
        final String checkName = checkName(check);
        final String time = duration == null ? forever : format(duration);
        granted.run(sender, text -> text
                .replace("<players>", joined)
                .replace("<check>", checkName)
                .replace("<duration>", time));
    }

    private void remove(
            final @NotNull CommandSender sender,
            final @NotNull Collection<Player> players,
            final @Nullable String check
    ) {
        final List<String> names = new ArrayList<>(players.size());
        for (final Player player : players) {
            final TrackedPlayerImpl tracked = playerRegistry.get(player.getUniqueId());
            if (tracked != null && tracked.removeBypass(check)) {
                names.add(player.getName());
            } else {
                cancelled.run(sender, text -> text.replace("<player>", player.getName()));
            }
        }
        if (names.isEmpty()) {
            return;
        }
        final String joined = String.join(", ", names);
        final String checkName = checkName(check);
        removed.run(sender, text -> text
                .replace("<players>", joined)
                .replace("<check>", checkName));
    }

    private void list(final @NotNull CommandSender sender) {
        final List<TrackedPlayerImpl> holders = playerRegistry.values().stream()
                .filter(player -> !player.bypasses().isEmpty())
                .toList();
        if (holders.isEmpty()) {
            listEmpty.run(sender);
            return;
        }
        final String count = String.valueOf(holders.size());
        listHeader.run(sender, text -> text.replace("<count>", count));
        final Instant now = Instant.now();
        for (final TrackedPlayerImpl player : holders) {
            for (final Map.Entry<String, Instant> entry : player.bypasses().entrySet()) {
                final String checkName = entry.getKey().equals(StorageConstants.ALL_CHECKS) ? allChecks : entry.getKey();
                final String left = entry.getValue() == null ? forever : format(Duration.between(now, entry.getValue()));
                listEntry.run(sender, text -> text
                        .replace("<player>", player.name())
                        .replace("<check>", checkName)
                        .replace("<duration>", left));
            }
        }
    }

    private @NotNull String checkName(final @Nullable String check) {
        return check == null ? allChecks : check.toLowerCase(Locale.ROOT);
    }

    private static @NotNull List<String> checkIds() {
        return List.copyOf(React.INSTANCE.configs().checks().keySet());
    }

    private static @NotNull SuggestionProvider<CommandSender> checkSuggestions() {
        return SuggestionProvider.blocking((context, input) ->
                checkIds().stream().map(Suggestion::suggestion).toList());
    }

    private static @NotNull String format(final @NotNull Duration duration) {
        final long seconds = Math.max(0, duration.getSeconds());
        final long[] parts = {seconds / 86_400, seconds / 3_600 % 24, seconds / 60 % 60, seconds % 60};
        final String[] units = {"d", "h", "m", "s"};
        final String text = java.util.stream.IntStream.range(0, parts.length)
                .filter(index -> parts[index] > 0)
                .mapToObj(index -> parts[index] + units[index])
                .collect(Collectors.joining(" "));
        return text.isEmpty() ? "0s" : text;
    }

}
