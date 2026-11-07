package com.g4vrk.react.command;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.config.model.ActionsConfig;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

/**
 * The {@code help} section of {@code actions.yml}: {@code /react} with no arguments and the
 * replies to command errors.
 */
public final class HelpActions implements ReloadObserver<ActionsConfig> {

    private final ActionParser<Audience> actionParser;

    private ExecutableActionList<? super Audience> root;
    private ExecutableActionList<? super Audience> noPermission;
    private ExecutableActionList<? super Audience> unknownCommand;
    private ExecutableActionList<? super Audience> playerOnly;

    public HelpActions(final @NotNull ActionParser<Audience> actionParser, final @NotNull ActionsConfig config) {
        this.actionParser = actionParser;
        this.onReload(config);
    }

    @Override
    public void onReload(final @NotNull ActionsConfig config) {
        final ActionsConfig.Help help = config.help();
        this.root = actionParser.parseAll(help.root());
        this.noPermission = actionParser.parseAll(help.noPermission());
        this.unknownCommand = actionParser.parseAll(help.unknownCommand());
        this.playerOnly = actionParser.parseAll(help.playerOnly());
    }

    public void root(final @NotNull Audience audience) {
        root.run(audience);
    }

    public void noPermission(final @NotNull Audience audience) {
        noPermission.run(audience);
    }

    public void unknownCommand(final @NotNull Audience audience) {
        unknownCommand.run(audience);
    }

    public void playerOnly(final @NotNull Audience audience) {
        playerOnly.run(audience);
    }

}
