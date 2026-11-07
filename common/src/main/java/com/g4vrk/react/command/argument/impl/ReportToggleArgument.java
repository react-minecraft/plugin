package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.channel.impl.ReportChannel;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code /react <literal> [players]} for a {@link ReportChannel}.
 *
 * <p>Without players it toggles reports of everyone. With players, e.g. {@code Steve,Alex}
 * or {@code @a}, the sender gets reports only about them until the next plain command.</p>
 */
public abstract class ReportToggleArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private final CommandBuilderFactory builderFactory;
    private final ActionParser<Audience> actionParser;

    private final ReportChannel channel;
    private final String permission;
    private final String literal;
    private final String[] aliases;
    private final Function<ActionsConfig, ActionsConfig.ReportToggle> section;

    private ExecutableActionList<? super Audience> enabledActions;
    private ExecutableActionList<? super Audience> enabledPlayersActions;
    private ExecutableActionList<? super Audience> disabledActions;

    protected ReportToggleArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull ReportChannel channel,
            final @NotNull String permission,
            final @NotNull Function<ActionsConfig, ActionsConfig.ReportToggle> section,
            final @NotNull String literal,
            final @NotNull String... aliases
    ) {
        this.builderFactory = builderFactory;
        this.actionParser = actionParser;
        this.channel = channel;
        this.permission = permission;
        this.section = section;
        this.literal = literal;
        this.aliases = aliases;

        this.onReload(React.INSTANCE.configs().actions());
    }

    @Override
    public final @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal(literal, aliases)
                .permission(permission)
                .optional("players", MultiplePlayerSelectorParser.multiplePlayerSelectorParser(false))
                .handler(context -> {

                    final CommandSender sender = context.sender();
                    final MultiplePlayerSelector selector = context.getOrDefault("players", null);

                    if (selector != null) {
                        this.follow(sender, selector.values());
                        return;
                    }

                    if (this.channel.remove(sender)) {
                        this.disabledActions.run(sender);
                    } else {
                        this.channel.subscribe(sender);
                        this.enabledActions.run(sender);
                    }
                });
    }

    private void follow(final @NotNull CommandSender sender, final @NotNull Collection<Player> players) {
        this.channel.subscribe(sender, players.stream().map(Player::getUniqueId).toList());

        final String names = players.stream().map(Player::getName).collect(Collectors.joining(", "));
        this.enabledPlayersActions.run(sender, text -> text.replace("<players>", names));
    }

    @Override
    public final void onReload(final @NotNull ActionsConfig config) {
        final ActionsConfig.ReportToggle toggle = this.section.apply(config);

        this.enabledActions = this.actionParser.parseAll(toggle.onEnable());
        this.enabledPlayersActions = this.actionParser.parseAll(toggle.onEnablePlayers());
        this.disabledActions = this.actionParser.parseAll(toggle.onDisable());
    }

}
