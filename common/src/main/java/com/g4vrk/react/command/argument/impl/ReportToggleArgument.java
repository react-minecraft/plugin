package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.channel.impl.ReportChannel;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.audience.Audience;
import org.incendo.cloud.Command;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * {@code /react <literal> [players]} for a {@link ReportChannel}.
 */
public abstract class ReportToggleArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private static final CloudKey<Collection<PlatformPlayer>> PLAYERS = CloudKey.of("players", new TypeToken<Collection<PlatformPlayer>>() {});
    private static final CloudKey<String> CHECK = CloudKey.of("check", String.class);

    private final CommandBuilderFactory builderFactory;
    private final PlatformCommandBridge platformCommands;
    private final ActionParser<Audience> actionParser;

    private final ReportChannel channel;
    private final String permission;
    private final String literal;
    private final String[] aliases;
    private final boolean byCheck;
    private final Function<ActionsConfig, ActionsConfig.ReportToggle> section;

    private volatile ExecutableActionList<? super Audience> enabledActions;
    private volatile ExecutableActionList<? super Audience> enabledPlayersActions;
    private volatile ExecutableActionList<? super Audience> disabledActions;

    protected ReportToggleArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull PlatformCommandBridge platformCommands,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull ReportChannel channel,
            final @NotNull String permission,
            final @NotNull Function<ActionsConfig, ActionsConfig.ReportToggle> section,
            final boolean byCheck,
            final @NotNull String literal,
            final @NotNull String... aliases
    ) {

        this.builderFactory = builderFactory;
        this.platformCommands = platformCommands;
        this.actionParser = actionParser;
        this.channel = channel;
        this.permission = permission;
        this.section = section;
        this.literal = literal;
        this.aliases = aliases;
        this.byCheck = byCheck;

        this.onReload(React.INSTANCE.configs().actions());

    }

    @Override
    public final @NotNull Command.Builder<PlatformSender> build() {

        Command.Builder<PlatformSender> builder = builderFactory.create()
                .literal(literal, aliases)
                .permission(permission)
                .argument(platformCommands.multiplePlayersArgument(PLAYERS, false));
        if (byCheck) {
            builder = builder.optional(
                    CHECK,
                    StringParser.stringParser(),
                    SuggestionProvider.blocking((context, input) -> {
                        final Set<String> checkIds = React.INSTANCE.configs().checks().keySet();
                        final List<Suggestion> suggestions = new ArrayList<>(checkIds.size());
                        for (final String checkId : checkIds) {
                            suggestions.add(Suggestion.suggestion(checkId));
                        }
                        return suggestions;
                    })
            );
        }
        return builder.handler(context -> {

            final PlatformSender sender = context.sender();
            final Collection<PlatformPlayer> players = context.getOrDefault(PLAYERS, null);
            final String check = context.getOrDefault(CHECK, null);

            if (players != null || check != null) {
                this.follow(sender, players, check);
                return;
            }

            if (this.channel.toggle(sender)) {
                this.enabledActions.run(sender);
            } else {
                this.disabledActions.run(sender);
            }
        });

    }

    private void follow(
            final @NotNull PlatformSender sender,
            final @Nullable Collection<PlatformPlayer> players,
            final @Nullable String check
    ) {

        final Set<UUID> uuids;
        final String names;
        if (players == null) {
            uuids = null;
            names = "*";
        } else {
            uuids = new HashSet<>(Math.max(4, players.size() * 2));
            final StringBuilder namesBuilder = new StringBuilder();
            for (final PlatformPlayer target : players) {
                uuids.add(target.uniqueId());
                if (!namesBuilder.isEmpty()) {
                    namesBuilder.append(", ");
                }
                namesBuilder.append(target.name());
            }
            names = namesBuilder.toString();
        }
        if (!this.channel.toggle(sender, uuids, check)) {
            this.disabledActions.run(sender);
            return;
        }

        final String checkName = check == null ? "*" : check;
        this.enabledPlayersActions.run(
                sender,
                text -> text
                        .replace("<players>", names)
                        .replace("<check>", checkName)
        );

    }

    @Override
    public final void onReload(
            final @NotNull ActionsConfig config
    ) {

        final ActionsConfig.ReportToggle toggle = this.section.apply(config);

        this.enabledActions = this.actionParser.parseAll(toggle.onEnable());
        this.enabledPlayersActions = this.actionParser.parseAll(toggle.onEnablePlayers());
        this.disabledActions = this.actionParser.parseAll(toggle.onDisable());

    }

}
