package com.g4vrk.react;

import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.react.event.SimpleEventBus;
import com.g4vrk.react.api.event.plugin.ReloadEvent;
import com.g4vrk.react.placeholder.BuiltinPlaceholders;
import com.g4vrk.react.placeholder.SimplePlaceholderRegistry;
import com.g4vrk.react.text.MiniTextFormatter;
import com.g4vrk.functionalActions.defaults.DefaultActions;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.react.api.ReactAPI;
import com.g4vrk.react.api.channel.ReactChannels;
import com.g4vrk.react.channel.ReportActions;
import com.g4vrk.react.config.ReactConfigs;
import com.g4vrk.react.config.lang.Language;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.factory.PlayerFactory;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.punish.PunishmentManager;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.service.CommandService;
import com.g4vrk.react.service.ExtensionService;
import com.g4vrk.react.service.ListenerService;
import com.g4vrk.react.storage.StorageManager;
import com.g4vrk.react.storage.config.DatabaseSettingsFactory;
import com.g4vrk.react.util.PluginUtil;
import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import com.g4vrk.text.LegacySerializers;
import com.g4vrk.text.Serializers;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import net.kyori.adventure.audience.Audience;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.ConfigurateException;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class React {

    public static final React INSTANCE = new React();

    private static final String[] SPECIAL_THANKS_TO = new String[]{"jvm-argument", "g4vrk"};

    private final Logger logger = LoggerFactory.getLogger("React");
    private final Serializers serializers = LegacySerializers.INSTANCE;
    private final SimplePlaceholderRegistry placeholders = new SimplePlaceholderRegistry(logger);
    private final TextFormatter text = new MiniTextFormatter(serializers, placeholders);
    private final SimpleEventBus events = new SimpleEventBus(logger);

    private Plugin plugin;
    private SchedulaAPI schedulaAPI;
    private Language language;
    private ReactConfigs configs;

    private Scheduler scheduler;
    private ActionParser<Audience> actionParser;
    private StorageManager storageManager;
    private PlayerFactory playerFactory;
    private PlayerRegistry playerRegistry;
    private RCPHost rcpHost;

    private ReportActions alertActions;
    private ReportActions monitorActions;
    private PunishmentManager punishmentManager;
    private InferenceHistoryPrinter inferenceHistoryPrinter;

    private CommandService commands;
    private ExtensionService extensions;
    private ListenerService listeners;

    public void initialize(final @NotNull Plugin plugin, final @NotNull ReactAPI api) {
        if (placeholders.keys().isEmpty()) {
            BuiltinPlaceholders.register(placeholders);
        }

        this.plugin = plugin;
        this.schedulaAPI = api.schedulaAPI();
    }

    public void preLoad() {
    }

    public void load() {
        logger.info("Checking for 'PacketEvents' plugin...");
        if (!PluginUtil.containsPlugin("packetevents")) {
            logger.error("Plugin 'packetevents' cannot be found on this server!");
            logger.error("Please, install it, otherwise React will not work.");
            logger.error("For now, disabling our plugin.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        final File dataFolder = plugin.getDataFolder();
        //noinspection ResultOfMethodCallIgnored
        dataFolder.mkdirs();

        this.language = Language.resolve();
        final String languageName = language.name().toLowerCase(Locale.ROOT);
        logger.info("Language successfully resolved: {}", language.name());

        logger.info("Loading configurations...");
        this.configs = new ReactConfigs(dataFolder.toPath(), languageName, logger);
        this.loadConfigs();

        final ActionRegistry<Audience> actionRegistry = new SimpleActionRegistry<>(true);
        DefaultActions.Adventure.registerDefaults(actionRegistry, serializers.universalSerializer()::serialize, ";");
        this.actionParser = new SimpleActionParser<>(actionRegistry);

        logger.info("Loading addons...");
        this.extensions = new ExtensionService(dataFolder, logger);
        extensions.loadAddons();

        logger.info("Creating Data management modules...");
        this.storageManager = new StorageManager(logger, new DatabaseSettingsFactory()
                .create(configs.database(), configs.databaseType(), dataFolder));
        storageManager.start();

        this.playerFactory = new PlayerFactory();
        this.playerRegistry = new PlayerRegistry();
        this.scheduler = schedulaAPI.createScheduler();
        this.rcpHost = new RCPHost(plugin, scheduler, playerRegistry, logger);

        logger.info("Loading RCP providers...");
        extensions.loadProviders(rcpHost, languageName);

        this.alertActions = new ReportActions(actionParser, configs.actions().alerts().alert());
        this.monitorActions = new ReportActions(actionParser, configs.actions().monitor().message());
        ReactChannels.ALERTS.delivery(alertActions);
        ReactChannels.MONITOR.delivery(monitorActions);
        this.punishmentManager = new PunishmentManager(logger, plugin.getServer(), scheduler);
        this.inferenceHistoryPrinter = new InferenceHistoryPrinter(text);

        logger.info("Registering commands...");
        this.commands = new CommandService(plugin, logger);
        commands.register(actionParser, configs.actions(), scheduler, inferenceHistoryPrinter,
                playerRegistry, rcpHost, extensions.updater());

        this.listeners = new ListenerService(plugin, logger);
        listeners.register(configs, playerRegistry, playerFactory, storageManager, rcpHost);

        logger.info("Starting RCP providers and addons...");
        extensions.startProviders(scheduler);
        extensions.startAddons();

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
        this.loadConfigs();

        commands.reload(configs.actions());
        punishmentManager.reload();
        inferenceHistoryPrinter.reload();
        playerFactory.reload();
        listeners.reload();
        alertActions.actions(configs.actions().alerts().alert());
        monitorActions.actions(configs.actions().monitor().message());

        for (final TrackedPlayerImpl player : playerRegistry.values()) {
            player.checkManager.reload();
        }

        extensions.reload();
        events.post(new ReloadEvent());
    }

    public void terminate() {
        logger.info("Terminating the plugin...");

        if (extensions != null) {
            extensions.close();
        }
        if (listeners != null) {
            listeners.close();
        }

        try {
            if (storageManager != null) {
                logger.info("Flushing and closing database storage...");
                storageManager.close(playerRegistry == null ? Set.of() : playerRegistry.values());
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
        this.schedulaAPI = null;
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
