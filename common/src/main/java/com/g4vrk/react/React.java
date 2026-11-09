package com.g4vrk.react;

import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.react.event.SimpleEventBus;
import com.g4vrk.react.api.event.plugin.ReloadEvent;
import com.g4vrk.react.placeholder.BuiltinPlaceholders;
import com.g4vrk.react.placeholder.SimplePlaceholderRegistry;
import com.g4vrk.react.text.MiniTextFormatter;
import com.g4vrk.functionalActions.defaults.DefaultActionSet;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.react.api.ReactAPI;
import com.g4vrk.react.api.channel.BuiltinChannels;
import com.g4vrk.react.channel.DebugActions;
import com.g4vrk.react.channel.ReportActions;
import com.g4vrk.react.config.ReactConfigs;
import com.g4vrk.react.config.lang.Language;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.factory.PlayerFactory;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.punish.PunishmentManager;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.reboot.PluginReboot;

import com.g4vrk.react.extension.ExtensionContext;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.update.UpdateNotifier;
import com.g4vrk.react.update.UpdateService;
import com.g4vrk.react.service.CommandService;
import com.g4vrk.react.service.ListenerService;
import com.g4vrk.react.storage.StorageManager;
import com.g4vrk.react.storage.config.DatabaseSettingsFactory;
import com.g4vrk.react.util.PluginUtil;
import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import com.g4vrk.text.LegacySerializerRegistry;
import com.g4vrk.text.SerializerRegistry;
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
import java.nio.file.Path;
import java.util.List;
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
    private final SerializerRegistry serializers = LegacySerializerRegistry.INSTANCE;
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
    private DebugActions debugActions;
    private PunishmentManager punishmentManager;
    private InferenceHistoryPrinter inferenceHistoryPrinter;

    private UpdateNotifier updateNotifier;
    private UpdateService updates;
    private ExtensionManager extensions;

    private CommandService commands;
    private ListenerService listeners;

    public void initialize(final @NotNull Plugin plugin, final @NotNull ReactAPI api) {
        if (placeholders.keySet().isEmpty()) {
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
        DefaultActionSet.Adventure.registerDefaults(actionRegistry, text -> serializers.universalSerializer().serialize(text), ";");
        this.actionParser = new SimpleActionParser<>(actionRegistry);

        logger.info("Creating Data management modules...");
        this.storageManager = new StorageManager(
                logger,
                new DatabaseSettingsFactory()
                        .create(configs.database(), configs.databaseType(), dataFolder)
        );
        storageManager.start();

        this.playerFactory = new PlayerFactory();
        this.playerRegistry = new PlayerRegistry();
        this.scheduler = schedulaAPI.createScheduler();
        this.rcpHost = new RCPHost(playerRegistry);
        this.commands = new CommandService(plugin, logger);

        this.updateNotifier = new UpdateNotifier(
                events,
                actionParser,
                configs.actions(),
                () -> updates != null && updates.notifies(),
                () -> updates == null ? List.of() : updates.pending()
        );
        this.updates = new UpdateService(
                dataFolder.toPath(),
                languageName,
                logger,
                pluginSource(),
                () -> configs.main(),
                updateNotifier
        );
        this.punishmentManager = new PunishmentManager(logger, plugin.getServer(), scheduler, actionParser, events);
        this.extensions = new ExtensionManager(
                new ExtensionContext(
                        plugin,
                        dataFolder.toPath(),
                        logger,
                        scheduler,
                        events,
                        placeholders,
                        text,
                        actionParser,
                        commands.manager(),
                        playerRegistry,
                        rcpHost,
                        punishmentManager
                ),
                updates
        );

        this.alertActions = new ReportActions(actionParser, configs.actions().alerts().alert());
        this.monitorActions = new ReportActions(actionParser, configs.actions().monitor().message());
        BuiltinChannels.ALERTS.delivery(alertActions);
        BuiltinChannels.MONITOR.delivery(monitorActions);
        this.debugActions = new DebugActions(actionParser, configs.checks());
        BuiltinChannels.DEBUG.delivery(debugActions);
        this.inferenceHistoryPrinter = new InferenceHistoryPrinter(text);

        logger.info("Registering commands...");
        commands.register(
                actionParser,
                configs.actions(),
                scheduler,
                inferenceHistoryPrinter,
                playerRegistry,
                rcpHost,
                extensions,
                updates
        );

        this.listeners = new ListenerService(plugin, logger);
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

    public void terminate() {
        logger.info("Terminating the plugin...");

        if (updates != null) {
            updates.close();
        }
        if (extensions != null) {
            extensions.close();
        }
        if (listeners != null) {
            listeners.close();
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
        this.schedulaAPI = null;
        this.rcpHost = null;
        this.punishmentManager = null;
    }

    private @NotNull UpdateService.Plugin pluginSource() {
        Path jar;
        try {
            jar = PluginReboot.findJar(plugin).toPath();
        } catch (final IllegalStateException ex) {
            logger.warn("Plugin updates are off: {}", ex.getMessage());
            jar = null;
        }
        String prefix = "react-paper";
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            prefix = "react-folia";
        } catch (final ClassNotFoundException ignored) {
            // Not Folia.
        }
        return new UpdateService.Plugin(jar, plugin.getServer().getUpdateFolderFile().toPath(), prefix);
    }

    private void loadConfigs() {
        try {
            configs.load();
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("An internal error occurred when trying to load configurations", ex);
        }
    }

}
