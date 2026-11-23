package com.g4vrk.react.fabric.command;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.fabric.FabricPlatform;
import com.g4vrk.react.fabric.sender.FabricPlatformPlayer;
import com.g4vrk.react.fabric.sender.FabricPlatformSender;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.schedula.task.TickSchedule;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.leangen.geantyref.TypeToken;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.setting.ManagerSetting;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Connects React's Incendo Cloud command manager to Minecraft's live Brigadier dispatcher.
 */
public final class FabricCommandBridge implements PlatformCommandBridge {

    private final FabricPlatform platform;
    private final TaskScheduler<PlatformPlayer> scheduler;
    private final ReactCommandManager manager;
    private final Map<String, LiteralCommandNode<CommandSourceStack>> nativeRoots = new HashMap<>();
    private final Map<String, List<String>> rootAliases = new HashMap<>();

    public FabricCommandBridge(
            final @NotNull FabricPlatform platform,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    ) {

        this.platform = platform;
        this.scheduler = scheduler;
        this.manager = new ReactCommandManager(new FabricRegistrationHandler());
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

        final ParserDescriptor<PlatformSender, String> parser = StringParser.<PlatformSender>stringParser();
        final ParserDescriptor<PlatformSender, PlatformPlayer> mapped = parser.mapSuccess(
                PlatformPlayer.class,
                (context, input) -> CompletableFuture.completedFuture(singlePlayer(context.sender(), input))
        );
        return CommandComponent.<PlatformSender, PlatformPlayer>builder()
                .key(key)
                .parser(mapped)
                .suggestionProvider((context, input) -> playerSuggestions(input.remainingInput()))
                .required(required);

    }

    @Override
    public CommandComponent.@NotNull Builder<PlatformSender, Collection<PlatformPlayer>> multiplePlayersArgument(
            final @NotNull CloudKey<Collection<PlatformPlayer>> key,
            final boolean required
    ) {

        final ParserDescriptor<PlatformSender, String> parser = StringParser.<PlatformSender>stringParser();
        final ParserDescriptor<PlatformSender, Collection<PlatformPlayer>> mapped = parser.mapSuccess(
                new TypeToken<Collection<PlatformPlayer>>() {
                },
                (context, input) -> CompletableFuture.completedFuture(resolvePlayers(context.sender(), input))
        );
        return CommandComponent.<PlatformSender, Collection<PlatformPlayer>>builder()
                .key(key)
                .parser(mapped)
                .suggestionProvider((context, input) -> playerSuggestions(input.remainingInput()))
                .required(required);

    }

    @Override
    public void onCommandsChanged(
            final @NotNull String rootLabel
    ) {

        synchronized (manager) {
            registerChangedRoot(rootLabel);
        }
        scheduler.schedule(this::refreshCommandTrees, TickSchedule.instant());

    }

    public void registerExistingCommands() {

        synchronized (manager) {
            for (final Command<PlatformSender> command : manager.commands()) {
                registerCloudCommand(command);
            }
        }
        refreshCommandTrees();

    }

    private boolean registerCloudCommand(
            final @NotNull Command<PlatformSender> command
    ) {

        synchronized (manager) {
            final CommandComponent<PlatformSender> root = command.rootComponent();
            final String label = root.name().toLowerCase(Locale.ROOT);
            if (!platform.enabled()) {
                return true;
            }
            if (nativeRoots.containsKey(label)) {
                return true;
            }
            final CommandDispatcher<CommandSourceStack> dispatcher = platform.activeServer()
                    .getCommands()
                    .getDispatcher();
            if (dispatcher.getRoot().getChild(label) != null) {
                return false;
            }
            final LiteralCommandNode<CommandSourceStack> node = register(dispatcher, label);
            nativeRoots.put(label, node);
            final List<String> registeredAliases = new ArrayList<>();
            for (final String alias : root.aliases()) {
                final String normalizedAlias = alias.toLowerCase(Locale.ROOT);
                if (nativeRoots.containsKey(normalizedAlias)
                        || dispatcher.getRoot().getChild(normalizedAlias) != null) {
                    continue;
                }
                nativeRoots.put(normalizedAlias, register(dispatcher, normalizedAlias));
                registeredAliases.add(normalizedAlias);
            }
            rootAliases.put(label, registeredAliases);
            return true;
        }

    }

    private void unregisterCloudCommand(
            final @NotNull CommandComponent<PlatformSender> root
    ) {

        synchronized (manager) {
            final String label = root.name().toLowerCase(Locale.ROOT);
            final LiteralCommandNode<CommandSourceStack> node = nativeRoots.remove(label);
            if (node == null || !platform.enabled()) {
                return;
            }
            final CommandDispatcher<CommandSourceStack> dispatcher = platform.activeServer()
                    .getCommands()
                    .getDispatcher();
            dispatcher.getRoot().getChildren().remove(node);
            final List<String> aliases = rootAliases.remove(label);
            if (aliases != null) {
                for (final String alias : aliases) {
                    final LiteralCommandNode<CommandSourceStack> aliasNode = nativeRoots.remove(alias);
                    if (aliasNode != null) {
                        dispatcher.getRoot().getChildren().remove(aliasNode);
                    }
                }
            }
            scheduler.schedule(this::refreshCommandTrees, TickSchedule.instant());
        }

    }

    private void registerChangedRoot(
            final @NotNull String rootLabel
    ) {

        if (nativeRoots.containsKey(rootLabel.toLowerCase(Locale.ROOT))) {
            return;
        }
        for (final Command<PlatformSender> command : manager.commands()) {
            if (command.rootComponent().name().equalsIgnoreCase(rootLabel)) {
                registerCloudCommand(command);
                return;
            }
        }

    }

    private void refreshCommandTrees() {

        if (!platform.enabled()) {
            return;
        }
        for (final PlatformPlayer player : platform.onlinePlayers()) {
            platform.activeServer().getCommands().sendCommands((ServerPlayer) player.handle());
        }

    }

    private LiteralCommandNode<CommandSourceStack> register(
            final @NotNull CommandDispatcher<CommandSourceStack> dispatcher,
            final @NotNull String label
    ) {

        final LiteralArgumentBuilder<CommandSourceStack> root = LiteralArgumentBuilder
                .<CommandSourceStack>literal(label)
                .executes(context -> execute(context, label))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(
                        "arguments",
                        StringArgumentType.greedyString()
                )
                        .suggests((context, builder) -> suggest(context, builder, label))
                        .executes(context -> execute(context, label)));
        return dispatcher.register(root);

    }

    private int execute(
            final @NotNull CommandContext<CommandSourceStack> context,
            final @NotNull String label
    ) throws CommandSyntaxException {

        final String arguments;
        try {
            arguments = context.getArgument("arguments", String.class);
        } catch (final IllegalArgumentException ignored) {
            manager.commandExecutor().executeCommand(FabricPlatformSender.wrap(context.getSource()), label).join();
            return 1;
        }
        manager.commandExecutor().executeCommand(
                FabricPlatformSender.wrap(context.getSource()),
                arguments.isBlank() ? label : label + " " + arguments
        ).join();
        return 1;

    }

    private CompletableFuture<Suggestions> suggest(
            final @NotNull CommandContext<CommandSourceStack> context,
            final @NotNull SuggestionsBuilder builder,
            final @NotNull String label
    ) {

        final String input = label + (builder.getRemaining().isEmpty() ? " " : " " + builder.getRemaining());
        return manager.suggestionFactory()
                .suggest(FabricPlatformSender.wrap(context.getSource()), input)
                .thenApply(result -> {
                    for (final Suggestion suggestion : result.list()) {
                        builder.suggest(suggestion.suggestion());
                    }
                    return builder.build();
                });

    }

    private PlatformPlayer singlePlayer(
            final @NotNull PlatformSender sender,
            final @NotNull String input
    ) {

        final Collection<PlatformPlayer> players = resolvePlayers(sender, input);
        if (players.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one online player");
        }
        return players.iterator().next();

    }

    private Collection<PlatformPlayer> resolvePlayers(
            final @NotNull PlatformSender sender,
            final @NotNull String input
    ) {

        final CommandSourceStack source;
        if (sender instanceof final FabricPlatformSender fabricSender) {
            source = fabricSender.commandSource();
        } else {
            throw new IllegalArgumentException("Player selectors require a Fabric command source");
        }
        try {
            final EntitySelector selector = EntityArgument.players().parse(new StringReader(input));
            final List<ServerPlayer> selected = selector.findPlayers(source);
            final List<PlatformPlayer> players = new ArrayList<>(selected.size());
            for (final ServerPlayer player : selected) {
                players.add(new FabricPlatformPlayer(player));
            }
            if (players.isEmpty()) {
                throw new IllegalArgumentException("Expected at least one online player");
            }
            return players;
        } catch (final CommandSyntaxException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }

    }

    private CompletableFuture<? extends Iterable<? extends Suggestion>> playerSuggestions(
            final @NotNull String input
    ) {

        final String prefix = input.toLowerCase(Locale.ROOT);
        final List<Suggestion> suggestions = new ArrayList<>();
        for (final PlatformPlayer player : platform.onlinePlayers()) {
            if (player.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(Suggestion.suggestion(player.name()));
            }
        }
        for (final String selector : List.of("@a", "@p", "@r", "@s")) {
            if (selector.startsWith(prefix)) {
                suggestions.add(Suggestion.suggestion(selector));
            }
        }
        return CompletableFuture.completedFuture(suggestions);

    }

    /**
     * Applies React's permission checks to command sources provided by Fabric.
     */
    private final class ReactCommandManager extends CommandManager<PlatformSender> {

        private ReactCommandManager(
                final @NotNull CommandRegistrationHandler<PlatformSender> registrationHandler
        ) {

            super(ExecutionCoordinator.simpleCoordinator(), registrationHandler);
            registerCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);

        }

        @Override
        public boolean hasPermission(
                final @NotNull PlatformSender sender,
                final @NotNull String permission
        ) {

            return sender.hasPermission(permission);

        }

    }

    /**
     * Registers and removes Cloud command roots in Fabric's Brigadier dispatcher.
     */
    private final class FabricRegistrationHandler implements CommandRegistrationHandler<PlatformSender> {

        @Override
        public boolean registerCommand(
                final @NotNull Command<PlatformSender> command
        ) {

            return registerCloudCommand(command);

        }

        @Override
        public void unregisterRootCommand(
                final @NotNull CommandComponent<PlatformSender> rootCommand
        ) {

            unregisterCloudCommand(rootCommand);

        }

    }

}
