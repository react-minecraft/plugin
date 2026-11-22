package com.g4vrk.react.command.argument.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import net.kyori.adventure.audience.Audience;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

/**
 * Reloads React configuration and reports the result to the sender.
 */
public final class ReloadArgument extends LocalArgument implements ReloadObserver<ActionsConfig> {

    private final CommandBuilderFactory builderFactory;

    private final TaskScheduler<PlatformPlayer> scheduler;

    private final ActionParser<Audience> actionParser;

    private volatile ExecutableActionList<? super Audience> startActions;
    private volatile ExecutableActionList<? super Audience> finishActions;
    private volatile ExecutableActionList<? super Audience> failureActions;

    public ReloadArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull ActionParser<Audience> actionParser
    ) {

        this.builderFactory = builderFactory;
        this.scheduler = scheduler;
        this.actionParser = actionParser;

        this.reload();

    }

    @Override
    public @NotNull Command.Builder<PlatformSender> build() {

        return builderFactory.create()
                .literal("reload")
                .permission("react.command.reload")
                .handler(context -> {

                    final PlatformSender sender = context.sender();

                    startActions.run(sender);

                    React.INSTANCE.reloadAsync().whenComplete((ignored, failure) ->
                            scheduler.schedule(() -> {
                                if (failure != null) {
                                    React.INSTANCE.logger().error("React reload failed", failure);
                                    failureActions.run(sender);
                                    return;
                                }
                                finishActions.run(sender);
                            }, TickSchedule.instant())
                    );

                });

    }

    public void reload() {

        this.onReload(React.INSTANCE.configs().actions());

    }

    @Override
    public void onReload(final @NotNull ActionsConfig config) {

        this.startActions = this.actionParser.parseAll(config.reload().onStart());
        this.finishActions = this.actionParser.parseAll(config.reload().onFinish());
        this.failureActions = this.actionParser.parseAll(config.reload().onFailure());

    }

}
