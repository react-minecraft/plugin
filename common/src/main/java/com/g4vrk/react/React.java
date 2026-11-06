package com.g4vrk.react;

import com.g4vrk.functionalActions.defaults.DefaultActions;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.react.api.ReactAPI;
import com.g4vrk.react.api.addon.internal.manager.AddonManager;
import com.g4vrk.react.api.channel.ReactChannels;
import com.g4vrk.react.api.channel.bind.PermissionChannelBinder;
import com.g4vrk.react.api.channel.print.impl.AlertPrinter;
import com.g4vrk.react.api.channel.print.impl.VerbosePrinter;
import com.g4vrk.react.api.rcp.RCPLifecycle;
import com.g4vrk.react.api.rcp.internal.manager.RCPManager;
import com.g4vrk.react.check.processor.rotation.RotationFactory;
import com.g4vrk.react.check.processor.rotation.RotationProcessor;
import com.g4vrk.react.command.argument.impl.AlertsArgument;
import com.g4vrk.react.command.argument.impl.RebootArgument;
import com.g4vrk.react.listeners.packet.RotationListener;
import com.g4vrk.react.command.argument.impl.HistoryArgument;
import com.g4vrk.react.command.argument.impl.ReloadArgument;
import com.g4vrk.react.command.argument.impl.VerboseArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.command.HelpActions;
import com.g4vrk.react.rcp.update.RCPUpdater;
import com.g4vrk.schedula.task.TickSchedule;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.spongepowered.configurate.ConfigurateException;
import com.g4vrk.react.config.ReactConfigs;
import com.g4vrk.react.config.lang.Language;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.listeners.bukkit.CombatListener;
import com.g4vrk.react.listeners.bukkit.ConnectionListener;
import com.g4vrk.react.placeholder.ReactPlaceholderExpansion;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.factory.PlayerFactory;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.punish.PunishmentManager;
import com.g4vrk.react.resource.ResourceHolder;
import com.g4vrk.react.resource.impl.PluginResourceHolder;
import com.g4vrk.react.storage.StorageManager;
import com.g4vrk.react.storage.config.DatabaseSettings;
import com.g4vrk.react.storage.config.DatabaseSettingsFactory;
import com.g4vrk.react.util.PluginUtil;
import com.g4vrk.react.util.placeholder.PlaceholderAPIUtil;
import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import com.g4vrk.text.LegacySerializers;
import com.g4vrk.text.Serializers;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.incendo.cloud.brigadier.BrigadierSetting;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.command.argument.impl.RCPCommandArgument;
import com.g4vrk.react.command.argument.impl.RCPListArgument;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class React {

    public static final React INSTANCE = new React();

    private static final String NAME = "React";

    private static final String[] SPECIAL_THANKS_TO = new String[]{"jvm-argument", "g4vrk"};

    private final Logger logger = LoggerFactory.getLogger(NAME);

    private final Serializers serializers = LegacySerializers.INSTANCE;

    private Plugin plugin;
    private Language language;

    private Scheduler scheduler;

    private CommandBuilderFactory commandBuilderFactory;

    private AlertsArgument alertsArgument;
    private VerboseArgument verboseArgument;
    private ReloadArgument reloadArgument;

    private ActionRegistry<Audience> actionRegistry;
    private ActionParser<Audience> actionParser;

    private AddonManager addonManager;

    private RCPManager rcpManager;
    private RCPUpdater rcpUpdater;
    private RCPHost rcpHost;

    private ResourceHolder resourceHolder;

    private PlayerFactory playerFactory;
    private PlayerRegistry playerRegistry;

    private ReactConfigs configs;

    private HelpActions helpActions;

    private DatabaseSettings databaseSettings;
    private StorageManager storageManager;

    private SchedulaAPI schedulaAPI;

    private AlertPrinter alertPrinter;
    private VerbosePrinter verbosePrinter;

    private PunishmentManager punishmentManager;
    private InferenceHistoryPrinter inferenceHistoryPrinter;
    private ReactPlaceholderExpansion placeholderExpansion;

    private CombatListener combatListener;

    private final Set<PacketListenerCommon> registeredListeners = new ObjectOpenHashSet<>();

    private LegacyPaperCommandManager<CommandSender> commandManager;

    public void initialize(
            final @NotNull Plugin plugin,
            final @NotNull ReactAPI api
    ) {
        this.plugin = plugin;
        this.resourceHolder = new PluginResourceHolder(plugin);
        this.schedulaAPI = api.getSchedulaAPI();
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

        logger.info("Plugin 'PacketEvents' successfully found, our plugin might work normally.");

        logger.info("Creating commandManager for registering commands...");
        this.commandManager = LegacyPaperCommandManager.createNative(
                plugin,
                ExecutionCoordinator.simpleCoordinator()
        );

        logger.info("Creating commandBuilderFactory for building arguments...");
        this.commandBuilderFactory =
                new CommandBuilderFactory(
                        commandManager,
                        "react",
                        Permissions.COMMAND_USAGE,
                        "React anticheat main command",
                        new String[]{"reactac", "reactai", "ac"}
                );

        // Paper hands Brigadier commands over in its COMMANDS lifecycle event, which only fires while the
        // server starts. A React enabled later (/react reboot) registers through the Bukkit command map.
        final boolean enabledAtStartup = Bukkit.getCurrentTick() == 0;
        if (!enabledAtStartup) {
            logger.info("Enabled on a running server: registering commands without native Brigadier");
        }
        if (enabledAtStartup && commandManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            try {

                logger.info("Registering Native Brigadier support in commandManager...");
                commandManager.registerBrigadier();

                final CloudBrigadierManager<CommandSender, ?> cbm = commandManager.brigadierManager();

                cbm.settings().set(BrigadierSetting.FORCE_EXECUTABLE, true);

            } catch (final Throwable th) {

                logger.error("Failed to register Brigadier native completions. Falling back to standard completions.", th);

            }
        } else if (commandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {

            logger.info("Registering Asynchronous completions in commandManager...");
            commandManager.registerAsynchronousCompletions();

        }

        final File pluginDir = plugin.getDataFolder();

        //noinspection ResultOfMethodCallIgnored
        pluginDir.mkdirs();

        this.language = Language.resolve();
        final String languageNameLower = language.name().toLowerCase();

        logger.info("Language successfully resolved: {}", languageNameLower.toUpperCase());

        this.configs = new ReactConfigs(pluginDir.toPath(), languageNameLower, logger);

        logger.info("Loading configurations...");
        try {
            this.configs.load();
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("An internal error occurred when trying to load configurations", ex);
        }
        logger.info("Configurations successfully loaded.");

        this.actionRegistry = new SimpleActionRegistry<>(true);

        DefaultActions.Adventure.registerDefaults(actionRegistry, serializers.universalSerializer()::serialize, ";");

        this.actionParser = new SimpleActionParser<>(actionRegistry);

        logger.info("Loading addons...");
        this.loadAddons();

        logger.info("Creating Data management modules...");
        this.databaseSettings = new DatabaseSettingsFactory().create(configs.database(), configs.databaseType(), pluginDir);
        this.storageManager = new StorageManager(logger, databaseSettings);
        this.storageManager.start();

        this.playerFactory = new PlayerFactory();

        this.playerRegistry = new PlayerRegistry();

        this.scheduler = schedulaAPI.createScheduler();

        this.rcpHost = new RCPHost(plugin, scheduler, playerRegistry, logger);

        logger.info("Loading RCP providers...");
        this.loadRCP();

        logger.info("Creating Channel system...");

        final Set<Consumer<Player>> joinHandlers = new ObjectOpenHashSet<>();

        final PermissionChannelBinder alertsBinder =
                new PermissionChannelBinder(Permissions.ALERTS_ENABLE_ON_JOIN, ReactChannels.ALERTS, false);

        final PermissionChannelBinder verboseBinder =
                new PermissionChannelBinder(Permissions.VERBOSE_ENABLE_ON_JOIN, ReactChannels.VERBOSE, false);

        joinHandlers.add(alertsBinder::accept);
        joinHandlers.add(verboseBinder::accept);

        this.alertPrinter = new AlertPrinter(ReactChannels.ALERTS::publish, serializers.universalSerializer()::serialize);
        this.verbosePrinter = new VerbosePrinter(ReactChannels.VERBOSE::publish, serializers.universalSerializer()::serialize);

        logger.info("Creating Punishment manager...");
        this.punishmentManager = new PunishmentManager(
                logger,
                plugin.getServer(),
                scheduler,
                alertPrinter
        );

        logger.info("Creating inference history modules...");

        this.inferenceHistoryPrinter = new InferenceHistoryPrinter(serializers.universalSerializer()::serialize);

        logger.info("Registering command arguments...");

        this.alertsArgument = new AlertsArgument(
                commandBuilderFactory,
                actionParser
        );

        this.verboseArgument = new VerboseArgument(
                commandBuilderFactory,
                actionParser
        );

        this.reloadArgument = new ReloadArgument(
                commandBuilderFactory,
                scheduler,
                actionParser
        );

        this.helpActions = new HelpActions(actionParser, configs.actions());
        this.registerHelp();

        commandManager.command(alertsArgument.build());
        commandManager.command(verboseArgument.build());
        commandManager.command(reloadArgument.build());
        commandManager.command(new RebootArgument(commandBuilderFactory).build());
        commandManager.command(new HistoryArgument(commandBuilderFactory, inferenceHistoryPrinter, playerRegistry).build());
        commandManager.command(new RCPListArgument(commandBuilderFactory, rcpHost, rcpUpdater).build());
        commandManager.command(new RCPCommandArgument(commandBuilderFactory, rcpHost, helpActions).build());

        logger.info("React main command successfully prepared!");

        logger.info("Registering packet listeners...");
        this.registerPacketListeners(
                new RotationListener(playerRegistry, new RotationProcessor(new RotationFactory()))
        );

        final PluginManager pluginManager = plugin.getServer().getPluginManager();

        logger.info("Registering bukkit listeners...");

        combatListener = new CombatListener(playerRegistry);

        pluginManager.registerEvents(
                combatListener,
                plugin
        );

        final ConnectionListener connectionListener = new ConnectionListener(
                playerRegistry,
                playerFactory,
                storageManager,
                (joined) -> joinHandlers.forEach(consumer -> consumer.accept(joined)),
                (quit) -> rcpHost.quit(quit.getUniqueId())
        );
        pluginManager.registerEvents(connectionListener, plugin);

        for (final Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            connectionListener.registerPlayer(onlinePlayer);
        }

        if (PlaceholderAPIUtil.apiPresent()) {

            logger.info("Registering PlaceholderAPI expansion...");

            this.placeholderExpansion = new ReactPlaceholderExpansion(
                    plugin.getName(),
                    "g4vrk",
                    plugin.getDescription().getVersion(),
                    playerRegistry,
                    offlinePlayer -> {
                        final String status = rcpHost.status();
                        return status == null ? "Unavailable" : status;
                    }
            );

            this.placeholderExpansion.register();

        }

        logger.info("Starting RCP providers...");
        this.enableRCP();

        logger.info("Starting addons...");
        this.enableAddons();

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

        try {
            this.configs.load();
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("An internal error occurred when trying to load configurations", ex);
        }

        this.helpActions.onReload(configs.actions());
        this.alertsArgument.reload();
        this.verboseArgument.reload();
        this.reloadArgument.reload();

        this.punishmentManager.reload();
        this.inferenceHistoryPrinter.reload();

        this.playerFactory.reload();
        this.combatListener.reload();

        this.alertPrinter.reload();
        this.verbosePrinter.reload();

        for (final ReactPlayer player : this.playerRegistry.all()) {
            player.checkManager.reload();
        }

        this.reloadRCP();

    }

    public void terminate() {
        logger.info("Terminating the plugin...");

        try {
            logger.info("Stopping all addons...");
            this.disableAddons();
        } catch (final Exception ex) {
            logger.error("Could not disable addons", ex);
        }

        try {
            logger.info("Stopping all RCP providers...");
            this.disableRCP();
        } catch (final Exception ex) {
            logger.error("Could not disable RCP providers", ex);
        }

        try {
            logger.info("Unregistering listeners...");
            this.unregisterPacketListeners();
        } catch (final Exception ex) {
            this.logger.error("Could not unregister packet listeners", ex);
        }

        try {
            if (this.storageManager != null) {
                logger.info("Flushing and closing database storage...");
                this.storageManager.close(
                        this.playerRegistry == null ? Set.of() : this.playerRegistry.all()
                );
                this.storageManager = null;
            }
        } catch (final Exception ex) {
            this.logger.error("Could not close database storage", ex);
        }

        try {
            if (this.playerRegistry != null) {
                logger.info("Clearing player registry...");
                this.playerRegistry.clear();
            }
        } catch (final Exception ex) {
            this.logger.error("Could not clear player registry", ex);
        }

        try {
            if (this.placeholderExpansion != null) {
                this.placeholderExpansion.unregister();
                this.placeholderExpansion = null;
            }
        } catch (final Exception ex) {
            this.logger.error("Could not unregister PlaceholderAPI expansion", ex);
        }

        try {
            ReactChannels.ALERTS.clear();
            ReactChannels.VERBOSE.clear();
        } catch (final Exception ex) {
            this.logger.error("Could not clear channel recipients", ex);
        }

        this.configs = null;
        this.schedulaAPI = null;
        this.rcpHost = null;
        this.punishmentManager = null;

    }

    private void loadAddons() {

        this.addonManager = new AddonManager(
                plugin.getDataFolder(),
                logger
        );

        addonManager.load();

    }

    private void loadRCP() {

        this.rcpManager = new RCPManager(
                plugin.getDataFolder(),
                logger,
                rcpHost
        );

        this.rcpUpdater = new RCPUpdater(
                rcpManager.directory().toPath(),
                rcpManager.providersDirectory().toPath(),
                language.name().toLowerCase(),
                logger
        );

    }

    private void enableAddons() {

        if (addonManager != null) {
            addonManager.start();
        }

    }

    private void enableRCP() {

        final RCPManager manager = rcpManager;
        final RCPUpdater updater = rcpUpdater;
        if (manager == null || updater == null) {
            return;
        }

        scheduler.scheduleAsync(() -> {
            updater.update();
            try {
                manager.start();
            } catch (final IllegalStateException ex) {
                logger.debug("RCP manager closed before it started", ex);
            }
        }, TickSchedule.instant());

    }

    private void registerHelp() {
        commandManager.command(commandBuilderFactory.create().handler(context -> helpActions.root(context.sender())));
        commandManager.exceptionController()
                .registerHandler(NoPermissionException.class, context -> helpActions.noPermission(context.context().sender()))
                .registerHandler(InvalidSyntaxException.class, context -> helpActions.unknownCommand(context.context().sender()))
                .registerHandler(NoSuchCommandException.class, context -> helpActions.unknownCommand(context.context().sender()))
                .registerHandler(InvalidCommandSenderException.class, context -> helpActions.playerOnly(context.context().sender()));
    }

    private void reloadRCP() {

        if (rcpManager == null) {
            return;
        }

        for (final RCPLifecycle lifecycle : rcpManager.components()) {
            try {
                lifecycle.onReload();
            } catch (final Throwable ex) {
                logger.error("RCP provider {} could not reload", lifecycle.getClass().getName(), ex);
            }
        }
    }

    private void disableAddons() {

        if (addonManager == null) {
            return;
        }

        try {
            addonManager.close();
        } finally {
            addonManager = null;
        }
    }

    private void disableRCP() {

        if (rcpManager == null) {
            return;
        }

        try {
            rcpManager.close();
        } finally {
            rcpManager = null;
        }
    }

    private void registerPacketListeners(
            final @NotNull PacketListenerCommon @NotNull ... packetListeners
    ) {

        final EventManager eventManager = PacketEvents.getAPI().getEventManager();

        eventManager.registerListeners(packetListeners);

        this.registeredListeners.addAll(new ObjectArrayList<>(packetListeners));

    }

    private void unregisterPacketListeners() {

        if (this.registeredListeners.isEmpty()) {
            return;
        }

        try {
            final EventManager eventManager = PacketEvents.getAPI().getEventManager();

            eventManager.unregisterListeners(this.registeredListeners.toArray(PacketListenerCommon[]::new));

        } finally {
            this.registeredListeners.clear();
        }

    }
}
