package com.g4vrk.react;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.react.api.channel.registry.BuiltinChannelRegistry;
import com.g4vrk.react.api.channel.impl.ReportChannel;
import com.g4vrk.react.api.event.plugin.ReloadEvent;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.action.defaults.DefaultActionRegistry;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.react.channel.DebugActionRegistry;
import com.g4vrk.react.channel.ReportActionRegistry;
import com.g4vrk.react.config.ConfigManager;
import com.g4vrk.react.config.lang.Language;
import com.g4vrk.react.event.SimpleEventDispatcher;
import com.g4vrk.react.extension.environment.ExtensionContext;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.placeholder.registry.BuiltinPlaceholderRegistry;
import com.g4vrk.react.placeholder.registry.SimplePlaceholderRegistry;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.update.PlatformUpdateSource;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.factory.PlayerFactory;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.punish.PunishmentManager;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.service.CommandService;
import com.g4vrk.react.service.ListenerService;
import com.g4vrk.react.storage.StorageManager;
import com.g4vrk.react.storage.config.factory.DatabaseSettingsFactory;
import com.g4vrk.react.text.MiniTextFormatter;
import com.g4vrk.react.update.notify.UpdateNotifier;
import com.g4vrk.react.update.UpdateService;
import com.g4vrk.text.LegacySerializerRegistry;
import com.g4vrk.text.SerializerRegistry;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.ConfigurateException;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Pure-Java React core lifecycle and service registry.
 *
 * <p>Every server platform (Paper, Folia, Fabric, Minestom, Sponge, etc.) bootstraps React by
 * supplying a {@link ReactPlatform} implementation to {@link #initialize(ReactPlatform)}.</p>
 */
public final class React {

    public static final React INSTANCE = new React();

    private static final String[] SPECIAL_THANKS_TO = new String[]{"jvm-argument", "g4vrk"};

    private final Logger logger = LoggerFactory.getLogger("React");
    private final SerializerRegistry serializers = LegacySerializerRegistry.INSTANCE;
    private final SimplePlaceholderRegistry placeholders = new SimplePlaceholderRegistry(logger);
    private final TextFormatter text = new MiniTextFormatter(serializers, placeholders);
    private final SimpleEventDispatcher events = new SimpleEventDispatcher(logger);

    private @Nullable ReactPlatform platform;
    private @Nullable Language language;
    private @Nullable ConfigManager configs;

    private @Nullable TaskScheduler<PlatformPlayer> scheduler;
    private ActionParser<Audience> actionParser;
    private @Nullable StorageManager storageManager;
    private @Nullable PlayerFactory playerFactory;
    private @Nullable PlayerRegistry playerRegistry;
    private @Nullable RCPHost rcpHost;

    private @Nullable ReportActionRegistry alertActions;
    private @Nullable ReportActionRegistry monitorActions;
    private @Nullable DebugActionRegistry debugActions;
    private @Nullable PunishmentManager punishmentManager;
    private @Nullable InferenceHistoryPrinter inferenceHistoryPrinter;

    private @Nullable UpdateNotifier updateNotifier;
    private @Nullable UpdateService updates;
    private @Nullable ExtensionManager extensions;

    private @Nullable CommandService commands;
    private @Nullable ListenerService listeners;
    private volatile boolean terminated;

    private React() {

    }

    public void initialize(
            final @NotNull ReactPlatform platform
    ) {

        if (placeholders.keySet().isEmpty()) {
            BuiltinPlaceholderRegistry.register(placeholders);
        }

        this.platform = platform;

    }

    public void load() {

        this.terminated = false;
        final Path dataPath = platform.dataDirectory();
        final File dataFolder = dataPath.toFile();
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs() && !dataFolder.isDirectory()) {
            throw new IllegalStateException("Failed to create data directory: " + dataFolder.getAbsolutePath());
        }

        this.language = Language.resolve();
        final String languageName = language.name().toLowerCase(Locale.ROOT);
        logger.info("Language successfully resolved: {}", language.name());

        logger.info("Loading configurations...");
        this.configs = new ConfigManager(dataPath, languageName, logger);
        this.loadConfigs();

        this.scheduler = platform.createScheduler();
        final ActionRegistry<Audience> actionRegistry = new SimpleActionRegistry<>(true);
        DefaultActionRegistry.register(
                actionRegistry,
                platform,
                scheduler,
                logger,
                text -> serializers.universalSerializer().serialize(text),
                ";"
        );
        this.actionParser = new SimpleActionParser<>(actionRegistry);

        logger.info("Creating Data management modules...");
        this.storageManager = new StorageManager(
                logger,
                new DatabaseSettingsFactory()
                        .create(configs.database(), configs.databaseType(), dataFolder)
        );
        storageManager.start();

        this.playerFactory = new PlayerFactory(platform::isBedrockPlayer);
        this.playerRegistry = new PlayerRegistry();
        this.rcpHost = new RCPHost(playerRegistry);

        final PlatformCommandBridge platformCommands = platform.createCommands(scheduler);
        final PlatformListenerBridge platformListeners = platform.createListeners(logger);
        this.commands = new CommandService(platformCommands);

        this.updateNotifier = new UpdateNotifier(
                events,
                actionParser,
                configs.actions(),
                () -> updates != null && updates.notifies(),
                () -> updates == null ? List.of() : updates.pending()
        );
        final PlatformUpdateSource source = platform.updateSource(logger);
        this.updates = new UpdateService(
                dataPath,
                languageName,
                logger,
                new UpdateService.Plugin(
                        source.pluginJar(),
                        source.updateDirectory(),
                        source.artifactPrefix(),
                        source.currentVersion(),
                        platform.platformName()
                ),
                () -> configs.main(),
                updateNotifier
        );
        this.punishmentManager = new PunishmentManager(logger, platform, scheduler, actionParser, events);
        this.extensions = new ExtensionManager(
                new ExtensionContext(
                        platform,
                        dataPath,
                        logger,
                        scheduler,
                        events,
                        placeholders,
                        text,
                        actionParser,
                        platformCommands,
                        platformListeners,
                        playerRegistry,
                        rcpHost,
                        punishmentManager
                ),
                updates
        );

        this.alertActions = new ReportActionRegistry(actionParser, configs.actions().alerts().alert());
        this.monitorActions = new ReportActionRegistry(actionParser, configs.actions().monitor().message());
        BuiltinChannelRegistry.ALERTS.delivery(alertActions);
        BuiltinChannelRegistry.MONITOR.delivery(monitorActions);
        this.debugActions = new DebugActionRegistry(actionParser, configs.checks());
        BuiltinChannelRegistry.DEBUG.delivery(debugActions);
        this.inferenceHistoryPrinter = new InferenceHistoryPrinter(text);

        logger.info("Registering commands...");
        commands.register(
                platform,
                actionParser,
                configs.actions(),
                scheduler,
                inferenceHistoryPrinter,
                playerRegistry,
                rcpHost,
                extensions,
                updates
        );

        this.listeners = new ListenerService(platform, platformListeners, logger);
        listeners.register(configs, playerRegistry, playerFactory, storageManager, rcpHost, updateNotifier::join);

        logger.info("Starting addons and RCP providers...");
        final ExtensionManager activeExtensions = extensions;
        final UpdateService activeUpdates = updates;
        activeExtensions.start(activeUpdates::onStart).whenComplete((ignored, error) -> {
            if (error != null) {
                logger.error("Failed to start addons and RCP providers", error);
                return;
            }
            synchronized (this) {
                if (!terminated && updates == activeUpdates && extensions == activeExtensions) {
                    activeUpdates.start(activeExtensions);
                }
            }
        });

        logger.info(" ");
        logger.info("React successfully enabled!");
        logger.info(" ");
        logger.info("Thanks for using our AI powered products.");
        logger.info("Special thanks to {}", String.join(", ", SPECIAL_THANKS_TO));
        logger.info(" ");
        logger.info("Our telegram channel: https://telegram.me/react_ac");
        logger.info("Our official site: https://www.react-ac.space");
        logger.info(" ");

    }

    public @NotNull CompletableFuture<Void> reloadAsync() {

        return CompletableFuture.supplyAsync(this::reloadState)
                .thenCompose(future -> future)
                .thenRun(() -> {
                    if (!terminated) {
                        events.post(new ReloadEvent());
                    }
                });

    }

    public void reload() {

        reloadAsync().join();

    }

    private synchronized @NotNull CompletableFuture<Void> reloadState() {

        if (terminated || configs == null) {
            return CompletableFuture.completedFuture(null);
        }
        this.loadConfigs();

        commands.reload(configs.actions());
        punishmentManager.reload();
        inferenceHistoryPrinter.reload();
        playerFactory.reload();
        listeners.reload();
        alertActions.actions(configs.actions().alerts().alert());
        monitorActions.actions(configs.actions().monitor().message());
        debugActions.checks(configs.checks());
        updateNotifier.onReload(configs.actions());

        for (final TrackedPlayerImpl player : playerRegistry) {
            player.reloadChecks();
        }

        return extensions.reload();

    }

    public synchronized void terminate() {

        this.terminated = true;
        logger.info("Terminating the plugin...");

        if (extensions != null) {
            extensions.stopLoading();
        }
        if (updates != null) {
            updates.close();
        }
        if (extensions != null) {
            extensions.close();
        }
        if (listeners != null) {
            listeners.close();
        }

        for (final ReportChannel channel : List.of(BuiltinChannelRegistry.ALERTS, BuiltinChannelRegistry.MONITOR, BuiltinChannelRegistry.DEBUG)) {
            channel.clear();
            channel.delivery((recipient, report) -> {
            });
        }

        events.clear();

        try {
            if (storageManager != null) {
                logger.info("Flushing and closing database storage...");
                storageManager.close(playerRegistry == null ? Set.of() : playerRegistry.snapshot());
            }
        } catch (final Exception ex) {
            logger.error("Could not close database storage", ex);
        }

        try {
            if (playerRegistry != null) {
                logger.info("Clearing player registry...");
                playerRegistry.clear();
            }
        } catch (final Exception ex) {
            logger.error("Could not clear player registry", ex);
        }

        this.platform = null;
        this.language = null;
        this.configs = null;
        this.scheduler = null;
        this.actionParser = null;
        this.storageManager = null;
        this.playerFactory = null;
        this.playerRegistry = null;
        this.rcpHost = null;
        this.alertActions = null;
        this.monitorActions = null;
        this.debugActions = null;
        this.punishmentManager = null;
        this.inferenceHistoryPrinter = null;
        this.updateNotifier = null;
        this.updates = null;
        this.extensions = null;
        this.commands = null;
        this.listeners = null;

    }

    public @NotNull Logger logger() {

        return logger;

    }

    public @NotNull SerializerRegistry serializers() {

        return serializers;

    }

    public @NotNull SimplePlaceholderRegistry placeholders() {

        return placeholders;

    }

    public @NotNull TextFormatter text() {

        return text;

    }

    public @NotNull SimpleEventDispatcher events() {

        return events;

    }

    public @Nullable ReactPlatform platform() {

        return platform;

    }

    public @Nullable Language language() {

        return language;

    }

    public @Nullable ConfigManager configs() {

        return configs;

    }

    public @Nullable TaskScheduler<PlatformPlayer> scheduler() {

        return scheduler;

    }

    public ActionParser<Audience> actionParser() {

        return actionParser;

    }

    public @Nullable StorageManager storageManager() {

        return storageManager;

    }

    public @Nullable PlayerFactory playerFactory() {

        return playerFactory;

    }

    public @Nullable PlayerRegistry playerRegistry() {

        return playerRegistry;

    }

    public @Nullable RCPHost rcpHost() {

        return rcpHost;

    }

    public @Nullable ReportActionRegistry alertActions() {

        return alertActions;

    }

    public @Nullable ReportActionRegistry monitorActions() {

        return monitorActions;

    }

    public @Nullable DebugActionRegistry debugActions() {

        return debugActions;

    }

    public @Nullable PunishmentManager punishmentManager() {

        return punishmentManager;

    }

    public @Nullable InferenceHistoryPrinter inferenceHistoryPrinter() {

        return inferenceHistoryPrinter;

    }

    public @Nullable UpdateNotifier updateNotifier() {

        return updateNotifier;

    }

    public @Nullable UpdateService updates() {

        return updates;

    }

    public @Nullable ExtensionManager extensions() {

        return extensions;

    }

    public @Nullable CommandService commands() {

        return commands;

    }

    public @Nullable ListenerService listeners() {

        return listeners;

    }

    public boolean terminated() {

        return terminated;

    }

    private void loadConfigs() {

        try {
            configs.load();
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("An internal error occurred when trying to load configurations", ex);
        }

    }

}
