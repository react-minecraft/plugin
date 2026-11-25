package com.g4vrk.react.sponge.command;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.sponge.sender.SpongePlatformSender;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.setting.ManagerSetting;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandCompletion;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.command.registrar.CommandRegistrar;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.plugin.PluginContainer;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * Connects React's shared Incendo Cloud manager to Sponge's raw command registrar.
 */
public final class SpongeCommandBridge implements PlatformCommandBridge {

    private final PluginContainer container;
    private final ReactPlatform platform;
    private final Logger logger;
    private final TaskScheduler<PlatformPlayer> scheduler;
    private final SpongeCloudManager manager;
    private final SpongePlayerArgumentParser playerArguments;
    /*
     * Sponge API 12 exposes registration but no command unregistration; inactive roots are hidden
     * by their live Cloud command check after an extension removes its commands.
     */
    private final Map<String, SpongeRawCommand> nativeRoots = new HashMap<>();

    public SpongeCommandBridge(
            final @NotNull PluginContainer container,
            final @NotNull ReactPlatform platform,
            final @NotNull Logger logger,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    ) {

        this.container = container;
        this.platform = platform;
        this.logger = logger;
        this.scheduler = scheduler;
        this.manager = new SpongeCloudManager();
        this.playerArguments = new SpongePlayerArgumentParser(platform);
        this.manager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true);

    }

    @Override
    public @NotNull CommandManager<PlatformSender> manager() {

        return manager;

    }

    @Override
    public CommandComponent.@NotNull Builder<PlatformSender, PlatformPlayer> singlePlayerArgument(
            final @NotNull CloudKey<PlatformPlayer> key,
            final boolean required
    ) {

        return CommandComponent.<PlatformSender, PlatformPlayer>builder()
                .key(key)
                .parser(playerArguments.singlePlayer())
                .required(required);

    }

    @Override
    public CommandComponent.@NotNull Builder<PlatformSender, Collection<PlatformPlayer>> multiplePlayersArgument(
            final @NotNull CloudKey<Collection<PlatformPlayer>> key,
            final boolean required
    ) {

        return CommandComponent.<PlatformSender, Collection<PlatformPlayer>>builder()
                .key(key)
                .parser(playerArguments.multiplePlayers())
                .required(required);

    }

    @Override
    public void onCommandsChanged(final @NotNull String rootLabel) {

        synchronized (manager) {
            if (!nativeRoots.containsKey(rootLabel)) {
                final Command<PlatformSender> command = manager.commands().stream()
                        .filter(candidate -> candidate.rootComponent().name().equalsIgnoreCase(rootLabel))
                        .findFirst()
                        .orElse(null);
                if (command != null) {
                    registerNativeRoot(rootLabel, command.rootComponent().aliases().toArray(String[]::new));
                }
            }
        }
        scheduler.schedule(this::refreshCommandTrees, TickSchedule.instant());

    }

    private boolean registerCloudCommand(final Command<PlatformSender> command) {

        final String root = command.rootComponent().name();
        synchronized (manager) {
            if (nativeRoots.containsKey(root)) {
                return true;
            }
            return registerNativeRoot(root, command.rootComponent().aliases().toArray(String[]::new));
        }

    }

    private boolean registerNativeRoot(final String root, final String[] aliases) {

        final SpongeRawCommand rawCommand = new SpongeRawCommand(root);
        final CommandRegistrar<org.spongepowered.api.command.Command.Raw> registrar = Sponge.server()
                .commandManager()
                .registrar(org.spongepowered.api.command.Command.Raw.class)
                .orElse(null);
        if (registrar == null) {
            logger.error("Sponge does not expose a raw command registrar; /{} was not registered", root);
            return false;
        }
        try {
            registrar.register(container, rawCommand, root, aliases);
            nativeRoots.put(root, rawCommand);
            return true;
        } catch (final RuntimeException failure) {
            logger.error("Could not register Sponge command /{}", root, failure);
            return false;
        }

    }

    private void refreshCommandTrees() {

        if (!Sponge.isServerAvailable()) {
            return;
        }
        final org.spongepowered.api.command.manager.CommandManager commands = Sponge.server().commandManager();
        for (final PlatformPlayer player : platform.onlinePlayers()) {
            final ServerPlayer spongePlayer = player.unwrap(ServerPlayer.class);
            if (spongePlayer != null) {
                commands.updateCommandTreeForPlayer(spongePlayer);
            }
        }

    }

    /**
     * Supplies the shared Cloud registry and platform permission check.
     */
    private final class SpongeCloudManager extends CommandManager<PlatformSender> {

        private SpongeCloudManager() {

            super(ExecutionCoordinator.simpleCoordinator(), new SpongeRegistrationHandler());
            registerCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);

        }

        @Override
        public boolean hasPermission(final PlatformSender sender, final String permission) {

            return sender.hasPermission(permission);

        }

    }

    /**
     * Bridges Cloud's root registration callbacks to Sponge's static raw command roots.
     */
    private final class SpongeRegistrationHandler implements CommandRegistrationHandler<PlatformSender> {

        @Override
        public boolean registerCommand(final @NotNull Command<PlatformSender> command) {

            return registerCloudCommand(command);

        }

        /*
         * Sponge API 12 cannot unregister raw commands; SpongeRawCommand#canExecute checks the
         * live Cloud tree so removed roots become silent and can be registered again later.
         */
        @Override
        public void unregisterRootCommand(final @NotNull CommandComponent<PlatformSender> rootCommand) {

        }

    }

    /**
     * Routes one registered Sponge root command into the active Cloud command tree.
     */
    private final class SpongeRawCommand implements org.spongepowered.api.command.Command.Raw {

        private final String root;

        private SpongeRawCommand(final String root) {

            this.root = root;

        }

        @Override
        public CommandResult process(
                final CommandCause cause,
                final ArgumentReader.Mutable arguments
        ) throws CommandException {

            final String remainder = arguments.remaining();
            final String input = remainder.isBlank() ? root : root + " " + remainder;
            try {
                manager.commandExecutor().executeCommand(
                        SpongePlatformSender.wrap(cause),
                        input
                ).join();
                return CommandResult.success();
            } catch (final CompletionException failure) {
                final Throwable causeFailure = failure.getCause() == null ? failure : failure.getCause();
                final String message = causeFailure.getMessage() == null ? "Command failed" : causeFailure.getMessage();
                throw new CommandException(Component.text(message), causeFailure);
            }

        }

        @Override
        public java.util.List<CommandCompletion> complete(
                final CommandCause cause,
                final ArgumentReader.Mutable arguments
        ) throws CommandException {

            final String remainder = arguments.remaining();
            final String input = root + (remainder.isEmpty() ? " " : " " + remainder);
            try {
                return manager.suggestionFactory().suggest(
                        SpongePlatformSender.wrap(cause),
                        input
                ).join().list().stream()
                        .map(suggestion -> CommandCompletion.of(suggestion.suggestion()))
                        .toList();
            } catch (final CompletionException failure) {
                final Throwable causeFailure = failure.getCause() == null ? failure : failure.getCause();
                throw new CommandException(Component.text("Could not build command suggestions"), causeFailure);
            }

        }

        @Override
        public boolean canExecute(final CommandCause cause) {

            return manager.commands().stream().anyMatch(command ->
                    command.rootComponent().name().equalsIgnoreCase(root));

        }

        @Override
        public java.util.Optional<Component> shortDescription(final CommandCause cause) {

            return java.util.Optional.of(Component.text("React command"));

        }

        @Override
        public java.util.Optional<Component> extendedDescription(final CommandCause cause) {

            return java.util.Optional.empty();

        }

        @Override
        public Component usage(final CommandCause cause) {

            return Component.text("/" + root);

        }

    }

}
