package com.g4vrk.react;

import com.g4vrk.functionalActions.defaults.DefaultActionSet;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.react.action.PlaySoundAction;
import com.g4vrk.react.api.channel.BuiltinChannelRegistry;
import com.g4vrk.react.api.channel.impl.ReportChannel;
import com.g4vrk.react.api.event.plugin.ReloadEvent;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.react.channel.DebugActionRegistry;
import com.g4vrk.react.channel.ReportActionRegistry;
import com.g4vrk.react.config.ConfigManager;
import com.g4vrk.react.config.lang.Language;
import com.g4vrk.react.event.SimpleEventDispatcher;
import com.g4vrk.react.extension.ExtensionContext;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.placeholder.BuiltinPlaceholderRegistry;
import com.g4vrk.react.placeholder.SimplePlaceholderRegistry;
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
import com.g4vrk.react.storage.config.DatabaseSettingsFactory;
import com.g4vrk.react.text.MiniTextFormatter;
import com.g4vrk.react.update.UpdateNotifier;
import com.g4vrk.react.update.UpdateService;
import com.g4vrk.text.LegacySerializerRegistry;
import com.g4vrk.text.SerializerRegistry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
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
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class React {

    public static final React INSTANCE = new React();

    private static final String[] SPECIAL_THANKS_TO = new String[]{"jvm-argument", "g4vrk"};

    private final Logger logger = LoggerFactory.getLogger("React");
    private final SerializerRegistry serializers = LegacySerializerRegistry.INSTANCE;
    private final SimplePlaceholderRegistry placeholders = new SimplePlaceholderRegistry(logger);
    private final TextFormatter text = new MiniTextFormatter(serializers, placeholders);
    private final SimpleEventDispatcher events = new SimpleEventDispatcher(logger);

    private ReactPlatform platform;
    private Language language;
    private ConfigManager configs;

    private TaskScheduler<PlatformPlayer> scheduler;
    private ActionParser<Audience> actionParser;
    private StorageManager storageManager;
    private PlayerFactory playerFactory;
    private PlayerRegistry playerRegistry;
    private RCPHost rcpHost;

    private ReportActionRegistry alertActions;
    private ReportActionRegistry monitorActions;
    private DebugActionRegistry debugActions;
    private PunishmentManager punishmentManager;
    private InferenceHistoryPrinter inferenceHistoryPrinter;

    private UpdateNotifier updateNotifier;
    private UpdateService updates;
    private ExtensionManager extensions;

    private CommandService commands;
    private ListenerService listeners;
    private volatile boolean terminated;

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
        //noinspection ResultOfMethodCallIgnored
        dataFolder.mkdirs();

        this.language = Language.resolve();
        final String languageName = language.name().toLowerCase(Locale.ROOT);
        logger.info("Language successfully resolved: {}", language.name());

        logger.info("Loading configurations...");
        this.configs = new ConfigManager(dataPath, languageName, logger);
        this.loadConfigs();

        final ActionRegistry<Audience> actionRegistry = new SimpleActionRegistry<>(true);
        DefaultActionSet.Adventure.registerDefaults(
                actionRegistry,
                text -> serializers.universalSerializer().serialize(text),
                ";"
        );
        actionRegistry.register(new PlaySoundAction(logger, platform::resolveSoundKey), "sound", "play-sound");
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
        this.scheduler = platform.createScheduler();
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
                        source.currentVersion()
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
        extensions.start(updates::onStart).thenRun(() -> updates.start(extensions));

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

        return CompletableFuture.runAsync(this::reload);

    }

    public synchronized void reload() {

        if (terminated || configs == null) {
            return;
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
            player.checkManager.reload();
        }

        extensions.reload();
        events.post(new ReloadEvent());

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

        try {
            if (storageManager != null) {
                logger.info("Flushing and closing database storage...");
                storageManager.close(playerRegistry == null ? Set.of() : playerRegistry.stream().toList());
                storageManager = null;
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

        this.configs = null;
        this.rcpHost = null;
        this.punishmentManager = null;

    }

    private void loadConfigs() {

        try {
            configs.load();
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("An internal error occurred when trying to load configurations", ex);
        }

    }

}
