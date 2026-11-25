package com.g4vrk.react.minestom.command;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.minestom.MinestomPlatform;
import com.g4vrk.react.minestom.sender.MinestomPlatformPlayer;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import io.leangen.geantyref.TypeToken;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.setting.ManagerSetting;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cloud command adapter that routes Minestom command roots into React's shared command manager.
 */
public final class MinestomCommandBridge implements PlatformCommandBridge {

    private final MinestomPlatform platform;
    private final Map<String, Command> routes = new LinkedHashMap<>();
    private final ReactCommandManager manager;

    public MinestomCommandBridge(
            final @NotNull MinestomPlatform platform
    ) {

        this.platform = platform;
        this.manager = new ReactCommandManager(new NativeCommandRegistrationHandler());
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

        final var parser = StringParser.<PlatformSender>stringParser();
        final var mapped = parser.mapSuccess(
                PlatformPlayer.class,
                (context, input) -> CompletableFuture.completedFuture(singlePlayer(context.sender(), input))
        );
        return CommandComponent.<PlatformSender, PlatformPlayer>builder()
                .key(key)
                .parser(mapped)
                .required(required);

    }

    @Override
    public CommandComponent.@NotNull Builder<PlatformSender, Collection<PlatformPlayer>> multiplePlayersArgument(
            final @NotNull CloudKey<Collection<PlatformPlayer>> key,
            final boolean required
    ) {

        final var parser = StringParser.<PlatformSender>stringParser();
        final var mapped = parser.mapSuccess(
                new TypeToken<Collection<PlatformPlayer>>() {},
                (context, input) -> CompletableFuture.completedFuture(resolvePlayers(context.sender(), input))
        );
        return CommandComponent.<PlatformSender, Collection<PlatformPlayer>>builder()
                .key(key)
                .parser(mapped)
                .required(required);

    }

    @Override
    public void onCommandsChanged(
            final @NotNull String rootLabel
    ) {

        final Command route = routes.get(rootLabel.toLowerCase(Locale.ROOT));
        if (route != null) {
            for (final Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                player.sendPacket(MinecraftServer.getCommandManager().createDeclareCommandsPacket(player));
            }
        }

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

        if (!input.startsWith("@")) {
            final PlatformPlayer player = platform.player(input);
            if (player == null) {
                throw new IllegalArgumentException("No online player named '" + input + "'");
            }
            return List.of(player);
        }

        final List<PlatformPlayer> online = new ArrayList<>(platform.onlinePlayers());
        if (online.isEmpty()) {
            throw new IllegalArgumentException("There are no online players");
        }
        return switch (input) {
            case "@a" -> List.copyOf(online);
            case "@s" -> {
                if (!(sender.handle() instanceof final Player player)) {
                    throw new IllegalArgumentException("@s requires a player sender");
                }
                yield List.of(new MinestomPlatformPlayer(player, platform.permissionChecker()));
            }
            case "@r" -> List.of(online.get(ThreadLocalRandom.current().nextInt(online.size())));
            case "@p" -> List.of(nearestPlayer(sender, online));
            default -> throw new IllegalArgumentException("Unsupported player selector: " + input);
        };

    }

    private PlatformPlayer nearestPlayer(
            final @NotNull PlatformSender sender,
            final @NotNull List<PlatformPlayer> online
    ) {

        if (!(sender.handle() instanceof final Player source) || source.getInstance() == null) {
            return online.getFirst();
        }
        final double sourceX = source.getPosition().x();
        final double sourceY = source.getPosition().y();
        final double sourceZ = source.getPosition().z();
        PlatformPlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (final PlatformPlayer candidate : online) {
            final Player player = (Player) candidate.handle();
            if (player.getInstance() != source.getInstance()) {
                continue;
            }
            final double x = player.getPosition().x() - sourceX;
            final double y = player.getPosition().y() - sourceY;
            final double z = player.getPosition().z() - sourceZ;
            final double distance = x * x + y * y + z * z;
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest == null ? online.getFirst() : nearest;

    }

    private void execute(
            final @NotNull CommandSender sender,
            final @NotNull CommandContext context
    ) {

        manager.commandExecutor().executeCommand(platform.adaptSender(sender), cloudInput(context)).join();

    }

    private String cloudInput(
            final @NotNull CommandContext context
    ) {

        String input = context.getInput().strip();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        final String root = context.getCommandName();
        if (!input.equals(root) && !input.startsWith(root + " ")) {
            return input.isEmpty() ? root : root + " " + input;
        }
        return input;

    }

    private Command createRoute(
            final @NotNull String label,
            final @NotNull String @NotNull [] aliases
    ) {

        final Command route = new Command(label, aliases);
        final ArgumentStringArray input = new ArgumentStringArray("arguments");
        input.setSuggestionCallback((sender, context, suggestion) -> {
            final var suggestions = manager.suggestionFactory()
                    .suggestImmediately(platform.adaptSender(sender), cloudInput(context));
            for (final Suggestion candidate : suggestions.list()) {
                suggestion.addEntry(new SuggestionEntry(candidate.suggestion()));
            }
        });
        route.addSyntax(this::execute, input);
        route.setDefaultExecutor(this::execute);
        return route;

    }

    private final class NativeCommandRegistrationHandler implements CommandRegistrationHandler<PlatformSender> {

        @Override
        public boolean registerCommand(
                final org.incendo.cloud.@NotNull Command<PlatformSender> command
        ) {

            synchronized (manager) {
                final CommandComponent<PlatformSender> root = command.rootComponent();
                final String label = root.name().toLowerCase(Locale.ROOT);
                if (routes.containsKey(label)) {
                    return true;
                }
                if (MinecraftServer.getCommandManager().commandExists(label)) {
                    return false;
                }
                final Collection<String> rawAliases = root.aliases();
                final List<String> aliasList = new ArrayList<>(rawAliases.size());
                for (final String rawAlias : rawAliases) {
                    final String lower = rawAlias.toLowerCase(Locale.ROOT);
                    if (!lower.equals(label)) {
                        aliasList.add(lower);
                    }
                }
                final String[] aliases = aliasList.toArray(new String[0]);
                for (final String alias : aliases) {
                    if (MinecraftServer.getCommandManager().commandExists(alias)) {
                        return false;
                    }
                }
                final Command route = createRoute(label, aliases);
                MinecraftServer.getCommandManager().register(route);
                routes.put(label, route);
                for (final String alias : aliases) {
                    routes.put(alias, route);
                }
                return true;
            }

        }

        @Override
        public void unregisterRootCommand(
                final @NotNull CommandComponent<PlatformSender> rootCommand
        ) {

            synchronized (manager) {
                final String label = rootCommand.name().toLowerCase(Locale.ROOT);
                final Command route = routes.remove(label);
                if (route == null) {
                    return;
                }
                for (final String alias : route.getAliases()) {
                    routes.remove(alias.toLowerCase(Locale.ROOT), route);
                }
                MinecraftServer.getCommandManager().unregister(route);
            }

        }

    }

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

}
