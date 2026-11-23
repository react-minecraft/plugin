package com.g4vrk.react.service;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.PermissionKey;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.command.HelpActionRegistry;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.argument.impl.AlertsArgument;
import com.g4vrk.react.command.argument.impl.BypassArgument;
import com.g4vrk.react.command.argument.impl.CheckListArgument;
import com.g4vrk.react.command.argument.impl.DebugArgument;
import com.g4vrk.react.command.argument.impl.HistoryArgument;
import com.g4vrk.react.command.argument.impl.MonitorArgument;
import com.g4vrk.react.command.argument.impl.RCPListArgument;
import com.g4vrk.react.command.argument.impl.RebootArgument;
import com.g4vrk.react.command.argument.impl.ReloadArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.update.UpdateService;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.audience.Audience;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The {@code /react} command: its platform-agnostic Cloud manager, every argument,
 * the help replies, and the error handlers.
 */
@Accessors(fluent = true)
public final class CommandService {

    private static final CloudKey<String> UNKNOWN = CloudKey.of("unknown", String.class);

    private final PlatformCommandBridge platformCommands;
    @Getter
    private final CommandManager<PlatformSender> manager;
    @Getter
    private final CommandBuilderFactory builderFactory;

    private List<ReloadObserver<ActionsConfig>> observers = List.of();

    public CommandService(
            final @NotNull PlatformCommandBridge platformCommands
    ) {

        this.platformCommands = platformCommands;
        this.manager = platformCommands.manager();
        this.builderFactory = new CommandBuilderFactory(
                manager,
                "react",
                PermissionKey.COMMAND_USAGE,
                "React anticheat main command",
                new String[]{"reactac", "reactai", "ac"}
        );

    }

    public void register(
            final @NotNull ReactPlatform platform,
            final @NotNull ActionParser<Audience> parser,
            final @NotNull ActionsConfig actions,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull InferenceHistoryPrinter historyPrinter,
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull RCPHost host,
            final @NotNull ExtensionManager extensions,
            final @NotNull UpdateService updates
    ) {

        final HelpActionRegistry help = new HelpActionRegistry(parser, actions);
        final AlertsArgument alerts = new AlertsArgument(builderFactory, platformCommands, parser);
        final MonitorArgument monitor = new MonitorArgument(builderFactory, platformCommands, parser);
        final ReloadArgument reload = new ReloadArgument(builderFactory, scheduler, parser);
        final RebootArgument reboot = new RebootArgument(builderFactory, platform, parser, actions, extensions, scheduler);
        final RCPListArgument rcps = new RCPListArgument(builderFactory, parser, actions, host, updates);
        final DebugArgument debug = new DebugArgument(builderFactory, platformCommands, parser);
        final BypassArgument bypass = new BypassArgument(builderFactory, platformCommands, parser, actions, playerRegistry);
        this.observers = List.of(help, alerts, monitor, debug, bypass, reload, reboot, rcps);

        manager.command(builderFactory.create().handler(context -> help.root(context.sender())));
        manager.command(builderFactory.create()
                .literal("help")
                .handler(context -> help.root(context.sender())));
        manager.command(builderFactory.create()
                .required(UNKNOWN, StringParser.greedyStringParser())
                .handler(context -> help.unknownCommand(context.sender())));
        manager.exceptionController()
                .registerHandler(NoPermissionException.class, context -> help.noPermission(context.context().sender()))
                .registerHandler(ArgumentParseException.class, context -> help.unknownCommand(context.context().sender()))
                .registerHandler(InvalidSyntaxException.class, context -> help.unknownCommand(context.context().sender()))
                .registerHandler(NoSuchCommandException.class, context -> help.unknownCommand(context.context().sender()))
                .registerHandler(InvalidCommandSenderException.class, context -> help.playerOnly(context.context().sender()));

        for (final LocalArgument argument : List.of(
                alerts,
                monitor,
                debug,
                bypass,
                reload,
                reboot,
                new HistoryArgument(builderFactory, platformCommands, historyPrinter, playerRegistry, help::playerOnly),
                rcps,
                new CheckListArgument(builderFactory)
        )) {
            argument.builds().forEach(manager::command);
        }

    }

    public void reload(
            final @NotNull ActionsConfig actions
    ) {

        for (final ReloadObserver<ActionsConfig> observer : observers) {
            observer.onReload(actions);
        }

    }

}
