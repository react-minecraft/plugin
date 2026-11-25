package com.g4vrk.react.bukkit.command;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.bukkit.sender.BukkitPlatformPlayer;
import com.g4vrk.react.bukkit.sender.BukkitPlatformSender;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import io.leangen.geantyref.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.data.SinglePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.bukkit.parser.selector.SinglePlayerSelectorParser;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.setting.ManagerSetting;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bukkit/Paper Cloud command manager adapter supporting native Brigadier selectors and
 * post-startup command tree refreshes.
 */
public final class BukkitCommandBridge implements PlatformCommandBridge {

    private final Plugin plugin;
    private final TaskScheduler<PlatformPlayer> scheduler;
    private final LegacyPaperCommandManager<PlatformSender> manager;
    private final BukkitCommandTreeRefresh treeRefresh;

    public BukkitCommandBridge(
            final @NotNull Plugin plugin,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    ) {

        this.plugin = plugin;
        this.scheduler = scheduler;
        this.manager = new LegacyPaperCommandManager<>(
                plugin,
                ExecutionCoordinator.<PlatformSender>builder()
                        .parsingExecutor(this::withCommandLock)
                        .suggestionsExecutor(this::withCommandLock)
                        .build(),
                SenderMapper.create(
                        BukkitPlatformSender::wrap,
                        sender -> (CommandSender) sender.handle()
                )
        );
        /*
         * Addons and RCPs register and delete their commands after the server has started.
         */
        this.manager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true);
        this.treeRefresh = new BukkitCommandTreeRefresh(plugin, manager, scheduler);
        this.registerCompletions();

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

        final ParserDescriptor<PlatformSender, SinglePlayerSelector> nativeParser = SinglePlayerSelectorParser.singlePlayerSelectorParser();
        final ParserDescriptor<PlatformSender, PlatformPlayer> mapped = nativeParser.mapSuccess(
                PlatformPlayer.class,
                (context, selector) -> CompletableFuture.completedFuture(new BukkitPlatformPlayer(selector.single()))
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

        final ParserDescriptor<PlatformSender, MultiplePlayerSelector> nativeParser = MultiplePlayerSelectorParser.multiplePlayerSelectorParser(false);
        final ParserDescriptor<PlatformSender, Collection<PlatformPlayer>> mapped = nativeParser.mapSuccess(
                new TypeToken<Collection<PlatformPlayer>>() {},
                (context, selector) -> {
                    final Collection<Player> values = selector.values();
                    final List<PlatformPlayer> wrapped = new ArrayList<>(values.size());
                    for (final Player player : values) {
                        wrapped.add(new BukkitPlatformPlayer(player));
                    }
                    return CompletableFuture.completedFuture(wrapped);
                }
        );
        return CommandComponent.<PlatformSender, Collection<PlatformPlayer>>builder()
                .key(key)
                .parser(mapped)
                .required(required)
                .preprocessor((context, input) -> allowedSelector(input.remainingInput().stripLeading())
                        ? ArgumentParseResult.success(true)
                        : ArgumentParseResult.failure(new IllegalArgumentException("Expected a player selector")))
                .suggestionProvider((context, input) -> nativeParser.parser().suggestionProvider()
                        .suggestionsFuture(context, input)
                        .thenApply(suggestions -> {
                            final List<Suggestion> players = new ArrayList<>();
                            for (final Suggestion suggestion : suggestions) {
                                if (allowedSelector(suggestion.suggestion())) {
                                    players.add(suggestion);
                                }
                            }
                            return players;
                        }));

    }

    @Override
    public void onCommandsChanged(
            final @NotNull String rootLabel
    ) {

        this.treeRefresh.accept(rootLabel);

    }

    @Override
    public void runTreeMutation(
            final @NotNull Runnable mutation
    ) {

        if (!this.plugin.isEnabled() || Bukkit.isPrimaryThread()) {
            mutation.run();
            return;
        }
        final CompletableFuture<Void> completed = new CompletableFuture<>();
        try {
            this.scheduler.schedule(() -> {
                try {
                    mutation.run();
                    completed.complete(null);
                } catch (final Throwable failure) {
                    completed.completeExceptionally(failure);
                }
            }, TickSchedule.instant());
        } catch (final IllegalPluginAccessException stopped) {
            if (!this.plugin.isEnabled()) {
                mutation.run();
                return;
            }
            throw stopped;
        }
        try {
            completed.get(10L, TimeUnit.SECONDS);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while mutating command tree", interrupted);
        } catch (final ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Command tree mutation failed", failure.getCause());
        } catch (final TimeoutException timeout) {
            this.plugin.getLogger().warning("Timed out waiting for server thread command tree mutation; running directly");
            mutation.run();
        }

    }

    private void withCommandLock(
            final @NotNull Runnable task
    ) {

        synchronized (this.manager) {
            task.run();
        }

    }

    private void registerCompletions() {

        if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            manager.registerBrigadier();
            manager.brigadierManager().setNativeSuggestions(
                    new TypeToken<MultiplePlayerSelectorParser<PlatformSender>>() {},
                    false
            );
        } else if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            manager.registerAsynchronousCompletions();
        }

    }

    private static boolean allowedSelector(
            final @NotNull String token
    ) {

        return token.length() < 2 || token.charAt(0) != '@' || "aprs".indexOf(token.charAt(1)) >= 0;

    }

}
