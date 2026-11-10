package com.g4vrk.react.service;

import lombok.Getter;
import lombok.experimental.Accessors;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.Permissions;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.command.HelpActions;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.argument.impl.AlertsArgument;
import com.g4vrk.react.command.argument.impl.BypassArgument;
import com.g4vrk.react.command.argument.impl.DebugArgument;
import com.g4vrk.react.command.argument.impl.HistoryArgument;
import com.g4vrk.react.command.argument.impl.RCPListArgument;
import com.g4vrk.react.command.argument.impl.RebootArgument;
import com.g4vrk.react.command.argument.impl.ReloadArgument;
import com.g4vrk.react.command.argument.impl.MonitorArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.update.UpdateService;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.setting.ManagerSetting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;

/**
 * The {@code /react} command: its manager, every argument, the help replies and the error handlers.
 */
@Accessors(fluent = true)
public final class CommandService {

    private final Logger logger;
    @Getter
    private final LegacyPaperCommandManager<CommandSender> manager;
    @Getter
    private final CommandBuilderFactory builderFactory;

    private List<ReloadObserver<ActionsConfig>> observers = List.of();

    public CommandService(final @NotNull Plugin plugin, final @NotNull Logger logger) {
        this.logger = logger;
        this.manager = LegacyPaperCommandManager.createNative(
                plugin,
                ExecutionCoordinator.<CommandSender>builder()
                        .parsingExecutor(this::withCommandLock)
                        .suggestionsExecutor(this::withCommandLock)
                        .build()
        );
        // Addons and RCPs register and delete their commands after the server has started.
        this.manager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true);
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
            final @NotNull ExtensionManager extensions,
            final @NotNull UpdateService updates
    ) {
        final HelpActions help = new HelpActions(parser, actions);
        final AlertsArgument alerts = new AlertsArgument(builderFactory, parser);
        final MonitorArgument monitor = new MonitorArgument(builderFactory, parser);
        final ReloadArgument reload = new ReloadArgument(builderFactory, scheduler, parser);
        final RebootArgument reboot = new RebootArgument(builderFactory, parser, actions, extensions);
        final RCPListArgument rcps = new RCPListArgument(builderFactory, parser, actions, host, updates);
        final DebugArgument debug = new DebugArgument(builderFactory, parser);
        final BypassArgument bypass = new BypassArgument(builderFactory, parser, actions, playerRegistry);
        this.observers = List.of(help, alerts, monitor, debug, bypass, reload, reboot, rcps);

        manager.command(builderFactory.create().handler(context -> help.root(context.sender())));
        manager.exceptionController()
                .registerHandler(NoPermissionException.class, context -> help.noPermission(context.context().sender()))
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
                new HistoryArgument(builderFactory, historyPrinter, playerRegistry),
                rcps
        )) {
            argument.builds().forEach(manager::command);
        }
    }

    public void reload(final @NotNull ActionsConfig actions) {
        for (final ReloadObserver<ActionsConfig> observer : observers) {
            observer.onReload(actions);
        }
    }

    private void withCommandLock(final @NotNull Runnable task) {
        synchronized (this.manager) {
            task.run();
        }
    }

    private void registerCompletions() {
        // Paper hands Brigadier commands over only once, in its COMMANDS lifecycle event at startup.
        // Addons and RCPs add commands later (and again on every /react reboot), so the commands go
        // through the Bukkit command map, which takes registrations at any time.
        if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            logger.info("Registering Asynchronous completions in commandManager...");
            manager.registerAsynchronousCompletions();
        }
    }

}
