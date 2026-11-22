package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.PermissionKey;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.storage.constant.StorageConstant;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.audience.Audience;
import org.incendo.cloud.Command;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.standard.DurationParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.incendo.cloud.type.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * {@code /react bypass}: temporary bypasses, stored in the database.
 */
public final class BypassArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private static final String FOREVER = "forever";
    private static final CloudKey<Collection<PlatformPlayer>> PLAYERS = CloudKey.of("players", new TypeToken<Collection<PlatformPlayer>>() {});
    private static final CloudKey<Either<Duration, String>> DURATION = CloudKey.of("duration", new TypeToken<Either<Duration, String>>() {});
    private static final CloudKey<String> CHECK = CloudKey.of("check", String.class);

    private final CommandBuilderFactory builderFactory;
    private final PlatformCommandBridge platformCommands;
    private final ActionParser<Audience> actionParser;
    private final PlayerRegistry playerRegistry;

    private volatile ExecutableActionList<? super Audience> granted;
    private volatile ExecutableActionList<? super Audience> removed;
    private volatile ExecutableActionList<? super Audience> cancelled;
    private volatile ExecutableActionList<? super Audience> invalidCheck;
    private volatile ExecutableActionList<? super Audience> invalidDuration;
    private volatile ExecutableActionList<? super Audience> listHeader;
    private volatile ExecutableActionList<? super Audience> listEntry;
    private volatile ExecutableActionList<? super Audience> listEmpty;
    private volatile String allChecks;
    private volatile String forever;

    public BypassArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull PlatformCommandBridge platformCommands,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull ActionsConfig config,
            final @NotNull PlayerRegistry playerRegistry
    ) {

        this.builderFactory = builderFactory;
        this.platformCommands = platformCommands;
        this.actionParser = actionParser;
        this.playerRegistry = playerRegistry;
        this.onReload(config);

    }

    @Override
    public void onReload(
            final @NotNull ActionsConfig config
    ) {

        final ActionsConfig.Bypass bypass = config.bypass();
        this.granted = actionParser.parseAll(bypass.granted());
        this.removed = actionParser.parseAll(bypass.removed());
        this.cancelled = actionParser.parseAll(bypass.cancelled());
        this.invalidCheck = actionParser.parseAll(bypass.invalidCheck());
        this.invalidDuration = actionParser.parseAll(bypass.invalidDuration());
        this.listHeader = actionParser.parseAll(bypass.listHeader());
        this.listEntry = actionParser.parseAll(bypass.listEntry());
        this.listEmpty = actionParser.parseAll(bypass.listEmpty());
        this.allChecks = bypass.allChecks();
        this.forever = bypass.forever();

    }

    @Override
    public @NotNull Command.Builder<PlatformSender> build() {

        return root()
                .argument(platformCommands.multiplePlayersArgument(PLAYERS, true))
                .optional(
                        DURATION,
                        ArgumentParser.firstOf(DurationParser.durationParser(), StringParser.stringParser()),
                        SuggestionProvider.blocking((context, input) -> List.of(FOREVER, "10m", "1h", "1d")
                                .stream().map(Suggestion::suggestion).toList())
                )
                .optional(CHECK, StringParser.stringParser(), checkSuggestions())
                .handler(context -> {
                    final Collection<PlatformPlayer> players = context.get(PLAYERS);
                    final Either<Duration, String> time = context.getOrDefault(DURATION, null);
                    String check = context.getOrDefault(CHECK, null);
                    Duration duration = null;
                    if (time != null) {
                        duration = time.primary().orElse(null);
                        final String text = time.fallback().orElse(null);
                        if (text != null && !text.equalsIgnoreCase(FOREVER)) {
                            if (check != null || !checkIds().contains(text.toLowerCase(Locale.ROOT))) {
                                invalidDuration.run(context.sender());
                                return;
                            }
                            check = text;
                        }
                    }
                    grant(context.sender(), players, duration, check);
                });

    }

    @Override
    public @NotNull List<Command.Builder<PlatformSender>> builds() {

        return List.of(
                build(),
                root()
                        .literal("remove")
                        .argument(platformCommands.multiplePlayersArgument(PLAYERS, true))
                        .optional(CHECK, StringParser.stringParser(), checkSuggestions())
                        .handler(context -> {
                            final Collection<PlatformPlayer> players = context.get(PLAYERS);
                            remove(context.sender(), players, context.getOrDefault(CHECK, null));
                        }),
                root()
                        .literal("list")
                        .handler(context -> list(context.sender()))
        );

    }

    private @NotNull Command.Builder<PlatformSender> root() {

        return builderFactory.create()
                .literal("bypass")
                .permission(PermissionKey.BYPASS_COMMAND);

    }

    private void grant(
            final @NotNull PlatformSender sender,
            final @NotNull Collection<PlatformPlayer> players,
            final @Nullable Duration duration,
            final @Nullable String check
    ) {

        if (!validCheck(sender, check)) {
            return;
        }
        final Instant until = duration == null ? null : expiration(Instant.now(), duration);
        if (duration != null && until == null) {
            invalidDuration.run(sender);
            return;
        }
        final List<String> names = new ArrayList<>(players.size());
        for (final PlatformPlayer player : players) {
            final TrackedPlayerImpl tracked = playerRegistry.get(player.uniqueId());
            if (tracked != null && tracked.bypass(check, until)) {
                names.add(player.name());
            } else {
                cancelled.run(sender, text -> text.replace("<player>", player.name()));
            }
        }
        if (names.isEmpty()) {
            return;
        }
        final String joined = String.join(", ", names);
        final String checkName = checkName(check);
        final String time = duration == null ? forever : format(duration);
        granted.run(
                sender,
                text -> text
                        .replace("<players>", joined)
                        .replace("<check>", checkName)
                        .replace("<duration>", time)
        );

    }

    static @Nullable Instant expiration(
            final @NotNull Instant now,
            final @NotNull Duration duration
    ) {

        if (duration.isNegative() || duration.isZero()) {
            return null;
        }
        try {
            final Instant until = now.plus(duration);
            return until.toEpochMilli() == Long.MAX_VALUE ? null : until;
        } catch (final DateTimeException | ArithmeticException ignored) {
            return null;
        }

    }

    private void remove(
            final @NotNull PlatformSender sender,
            final @NotNull Collection<PlatformPlayer> players,
            final @Nullable String check
    ) {

        if (!validCheck(sender, check)) {
            return;
        }
        final List<String> names = new ArrayList<>(players.size());
        for (final PlatformPlayer player : players) {
            final TrackedPlayerImpl tracked = playerRegistry.get(player.uniqueId());
            if (tracked != null && tracked.removeBypass(check)) {
                names.add(player.name());
            } else {
                cancelled.run(sender, text -> text.replace("<player>", player.name()));
            }
        }
        if (names.isEmpty()) {
            return;
        }
        final String joined = String.join(", ", names);
        final String checkName = checkName(check);
        removed.run(
                sender,
                text -> text
                        .replace("<players>", joined)
                        .replace("<check>", checkName)
        );

    }

    private void list(
            final @NotNull PlatformSender sender
    ) {

        final List<TrackedPlayerImpl> holders = playerRegistry.stream()
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
                final String checkName = entry.getKey().equals(StorageConstant.ALL_CHECKS) ? allChecks : entry.getKey();
                final String left = entry.getValue() == null ? forever : format(Duration.between(now, entry.getValue()));
                listEntry.run(
                        sender,
                        text -> text
                                .replace("<player>", player.name())
                                .replace("<check>", checkName)
                                .replace("<duration>", left)
                );
            }
        }

    }

    private @NotNull String checkName(
            final @Nullable String check
    ) {

        return check == null ? allChecks : check.toLowerCase(Locale.ROOT);

    }

    private static @NotNull List<String> checkIds() {

        return List.copyOf(React.INSTANCE.configs().checks().keySet());

    }

    private boolean validCheck(
            final @NotNull PlatformSender sender,
            final @Nullable String check
    ) {

        if (check == null || checkIds().contains(check.toLowerCase(Locale.ROOT))) {
            return true;
        }
        invalidCheck.run(sender);
        return false;

    }

    private static @NotNull SuggestionProvider<PlatformSender> checkSuggestions() {

        return SuggestionProvider.blocking((context, input) ->
                checkIds().stream().map(Suggestion::suggestion).toList());

    }

    private static @NotNull String format(
            final @NotNull Duration duration
    ) {

        final long seconds = Math.max(0, duration.getSeconds());
        final long[] parts = {seconds / 86_400, seconds / 3_600 % 24, seconds / 60 % 60, seconds % 60};
        final String[] units = {"d", "h", "m", "s"};
        final String text = IntStream.range(0, parts.length)
                .filter(index -> parts[index] > 0)
                .mapToObj(index -> parts[index] + units[index])
                .collect(Collectors.joining(" "));
        return text.isEmpty() ? "0s" : text;

    }

}
