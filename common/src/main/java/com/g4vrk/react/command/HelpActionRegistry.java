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
public final class HelpActionRegistry implements ReloadObserver<ActionsConfig> {

    private final ActionParser<Audience> actionParser;

    private volatile ActionGroup actions;

    public HelpActionRegistry(final @NotNull ActionParser<Audience> actionParser, final @NotNull ActionsConfig config) {

        this.actionParser = actionParser;
        this.onReload(config);

    }

    @Override
    public void onReload(final @NotNull ActionsConfig config) {

        final ActionsConfig.Help help = config.help();
        this.actions = new ActionGroup(
                actionParser.parseAll(help.root()),
                actionParser.parseAll(help.noPermission()),
                actionParser.parseAll(help.unknownCommand()),
                actionParser.parseAll(help.playerOnly())
        );

    }

    public void root(final @NotNull Audience audience) {

        actions.root().run(audience);

    }

    public void noPermission(final @NotNull Audience audience) {

        actions.noPermission().run(audience);

    }

    public void unknownCommand(final @NotNull Audience audience) {

        actions.unknownCommand().run(audience);

    }

    public void playerOnly(final @NotNull Audience audience) {

        actions.playerOnly().run(audience);

    }

    private record ActionGroup(
            @NotNull ExecutableActionList<? super Audience> root,
            @NotNull ExecutableActionList<? super Audience> noPermission,
            @NotNull ExecutableActionList<? super Audience> unknownCommand,
            @NotNull ExecutableActionList<? super Audience> playerOnly
    ) {

    }

}
