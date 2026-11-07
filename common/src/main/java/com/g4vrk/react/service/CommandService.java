package com.g4vrk.react.service;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.Permissions;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.command.HelpActions;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.argument.impl.AlertsArgument;
import com.g4vrk.react.command.argument.impl.HistoryArgument;
import com.g4vrk.react.command.argument.impl.RCPArgumentRouter;
import com.g4vrk.react.command.argument.impl.RCPListArgument;
import com.g4vrk.react.command.argument.impl.RebootArgument;
import com.g4vrk.react.command.argument.impl.ReloadArgument;
import com.g4vrk.react.command.argument.impl.VerboseArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.rcp.update.RCPUpdater;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.brigadier.BrigadierSetting;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;

/**
 * The {@code /react} command: its manager, every argument, the help replies and the error handlers.
 */
public final class CommandService {

    private final Logger logger;
    private final LegacyPaperCommandManager<CommandSender> manager;
    private final CommandBuilderFactory builderFactory;

    private List<ReloadObserver<ActionsConfig>> observers = List.of();

    public CommandService(final @NotNull Plugin plugin, final @NotNull Logger logger) {
        this.logger = logger;
        this.manager = LegacyPaperCommandManager.createNative(plugin, ExecutionCoordinator.simpleCoordinator());
        this.builderFactory = new CommandBuilderFactory(
                manager,
                "react",
                Permissions.COMMAND_USAGE,
                "React anticheat main command",
                new String[]{"reactac", "reactai", "ac"}
        );
        this.registerCompletions();
    }

    public void register(
            final @NotNull ActionParser<Audience> parser,
            final @NotNull ActionsConfig actions,
            final @NotNull Scheduler scheduler,
            final @NotNull InferenceHistoryPrinter historyPrinter,
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull RCPHost host,
            final @NotNull RCPUpdater updater
    ) {
        final HelpActions help = new HelpActions(parser, actions);
        final AlertsArgument alerts = new AlertsArgument(builderFactory, parser);
        final VerboseArgument verbose = new VerboseArgument(builderFactory, parser);
        final ReloadArgument reload = new ReloadArgument(builderFactory, scheduler, parser);
        final RebootArgument reboot = new RebootArgument(builderFactory, parser, actions);
        final RCPListArgument rcps = new RCPListArgument(builderFactory, parser, actions, host, updater);
        this.observers = List.of(help, alerts, verbose, reload, reboot, rcps);

        manager.command(builderFactory.create().handler(context -> help.root(context.sender())));
        manager.exceptionController()
                .registerHandler(NoPermissionException.class, context -> help.noPermission(context.context().sender()))
                .registerHandler(InvalidSyntaxException.class, context -> help.unknownCommand(context.context().sender()))
                .registerHandler(NoSuchCommandException.class, context -> help.unknownCommand(context.context().sender()))
                .registerHandler(InvalidCommandSenderException.class, context -> help.playerOnly(context.context().sender()));

        for (final LocalArgument argument : List.of(
                alerts,
                verbose,
                reload,
                reboot,
                new HistoryArgument(builderFactory, historyPrinter, playerRegistry),
                rcps,
                new RCPArgumentRouter(builderFactory, host, help))) {
            manager.command(argument.build());
        }
    }

    public void reload(final @NotNull ActionsConfig actions) {
        for (final ReloadObserver<ActionsConfig> observer : observers) {
            observer.onReload(actions);
        }
    }

    private void registerCompletions() {
        // Paper hands Brigadier commands over in its COMMANDS lifecycle event, which only fires while the
        // server starts. A React enabled later (/react reboot) registers through the Bukkit command map.
        final boolean enabledAtStartup = Bukkit.getCurrentTick() == 0;
        if (!enabledAtStartup) {
            logger.info("Enabled on a running server: registering commands without native Brigadier");
        }
        if (enabledAtStartup && manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            try {
                logger.info("Registering Native Brigadier support in commandManager...");
                manager.registerBrigadier();
                manager.brigadierManager().settings().set(BrigadierSetting.FORCE_EXECUTABLE, true);
            } catch (final Throwable th) {
                logger.error("Failed to register Brigadier native completions. Falling back to standard completions.", th);
            }
        } else if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            logger.info("Registering Asynchronous completions in commandManager...");
            manager.registerAsynchronousCompletions();
        }
    }

}
