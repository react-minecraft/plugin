package com.g4vrk.react.update;

import com.g4vrk.react.api.config.ConfigFile;
import com.g4vrk.react.api.config.ConfigMigrations;
import com.g4vrk.react.api.event.plugin.UpdateDownloadedEvent;
import com.g4vrk.react.api.extension.GitHubSource;
import com.g4vrk.react.api.rcp.RCPAPI;
import com.g4vrk.react.config.model.MainConfig;
import com.g4vrk.react.extension.ExtensionDescriptor;
import com.g4vrk.react.extension.ExtensionKind;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.extension.InvalidExtensionException;
import com.g4vrk.react.extension.StagedUpdates;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps the plugin, the RCPs and the addons on their latest signed GitHub release.
 *
 * <p>The plugin jar is staged in the server's update folder and replaced on restart. RCP and
 * addon jars are staged in {@code update/staged} and put in place when the extension is
 * loaded again, on start or with {@code /react reboot}.</p>
 */
public final class UpdateService implements StagedUpdates, AutoCloseable {

    private static final String PLUGIN_REPOSITORY = "react-minecraft/plugin";
    private static final String REACT_RCP_PREFIX = "react-inference-rcp";
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(10);

    private final Path dataFolder;
    private final Path folder;
    private final String language;
    private final Logger logger;
    private final ReleaseClient client;
    private final Plugin plugin;
    private final Supplier<MainConfig> main;
    private final Consumer<UpdateDownloadedEvent> notifier;

    private final ConfigFile<UpdateSettings> settingsFile;
    private final Map<String, Status> statuses = new ConcurrentHashMap<>();
    private final Map<String, UpdateDownloadedEvent> downloaded = new ConcurrentHashMap<>();

    private volatile UpdateSettings settings = new UpdateSettings(Duration.ofHours(6), false, true);
    private volatile @Nullable ScheduledExecutorService timer;

    /**
     * Where the running plugin jar is and where its update goes.
     *
     * @param jar          the running jar, or {@code null} when it cannot be found
     * @param updateFolder the server's update folder, applied on restart
     * @param prefix       the start of the release jar name for this server
     */
    public record Plugin(@Nullable Path jar, @NotNull Path updateFolder, @NotNull String prefix) {

    }

    /**
     * What the last check of a component found, for {@code /react rcps}.
     *
     * @param version   the version of the release it follows, or {@code null}
     * @param channel   the channel it follows
     * @param lastCheck when GitHub last answered, or {@code null}
     * @param state     a short word on the outcome
     */
    public record Status(
            @Nullable String version,
            @NotNull UpdateChannel channel,
            @Nullable Instant lastCheck,
            @NotNull String state
    ) {

    }

    @ConfigSerializable
    record ReactRCPSettings(Settings settings) {

        @ConfigSerializable
        record Settings(boolean enabled) {

        }

    }

    private record Component(
            @NotNull UpdateDownloadedEvent.Kind kind,
            @NotNull String name,
            @NotNull String repository,
            @NotNull String prefix,
            @NotNull Path current,
            @NotNull Path target,
            @Nullable ExtensionKind extension
    ) {

    }

    public UpdateService(
            final @NotNull Path dataFolder,
            final @NotNull String language,
            final @NotNull Logger logger,
            final @NotNull Plugin plugin,
            final @NotNull Supplier<MainConfig> main,
            final @NotNull Consumer<UpdateDownloadedEvent> notifier
    ) {
        this.dataFolder = dataFolder;
        this.folder = dataFolder.resolve("update");
        this.language = language;
        this.logger = logger;
        this.client = new ReleaseClient(logger);
        this.plugin = plugin;
        this.main = main;
        this.notifier = notifier;
        this.settingsFile = ConfigFile.builder(UpdateSettings.class)
                .path(folder.resolve("settings.yml"))
                .defaults(UpdateService.class.getClassLoader(), language + "/update/settings.yml")
                .logger(logger)
                .build();
    }

    /**
     * Runs on the extension thread before any jar is loaded: installs or removes the React RCP,
     * downloads the official addons that are missing and stages every update.
     */
    public synchronized void onStart() {
        loadSettings();
        reactRCP();
        officialAddons();
        checkAll();
    }

    /**
     * Starts the periodic checks.
     *
     * @param manager rebooted after a download when {@code auto-reboot} is on
     */
    public void start(final @NotNull ExtensionManager manager) {
        final Duration interval = settings.interval() == null || settings.interval().compareTo(MIN_INTERVAL) < 0
                ? MIN_INTERVAL
                : settings.interval();
        final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "React-Updates");
            thread.setDaemon(true);
            return thread;
        });
        this.timer = timer;
        timer.scheduleWithFixedDelay(() -> tick(manager), interval.toSeconds(), interval.toSeconds(), TimeUnit.SECONDS);
    }

    private void tick(final @NotNull ExtensionManager manager) {
        try {
            loadSettings();
            final List<Component> staged = checkAll();
            if (!settings.autoReboot()) {
                return;
            }
            for (final Component component : staged) {
                if (component.extension() != null) {
                    manager.reboot(component.name());
                }
            }
        } catch (final RuntimeException failure) {
            logger.error("Could not check for updates", failure);
        }
    }

    /**
     * The last check of a repository.
     *
     * @param repository the repository, {@code owner/name}
     * @return the status, or {@code null} when it was never checked
     */
    public @Nullable Status status(final @NotNull String repository) {
        return statuses.get(repository.toLowerCase(Locale.ROOT));
    }

    /**
     * The last check of the React RCP.
     *
     * @return the status, or {@code null} when it was never checked
     */
    public @Nullable Status reactRCPStatus() {
        return status(ReleaseClient.REACT_RCP);
    }

    /**
     * Updates that are downloaded and not applied yet, for the join notice.
     *
     * @return the updates
     */
    public @NotNull Collection<UpdateDownloadedEvent> pending() {
        return List.copyOf(downloaded.values());
    }

    /**
     * Whether players are told about downloads.
     *
     * @return the {@code notify} setting
     */
    public boolean notifies() {
        return settings.notifications();
    }

    @Override
    public synchronized @NotNull Path apply(
            final @NotNull ExtensionKind kind,
            final @NotNull String name,
            final @NotNull Path jar
    ) {
        final Path directory = stagedFolder(kind, name);
        final List<Path> files = jars(directory);
        if (files.size() != 1) {
            return jar;
        }
        final Path target = jar.resolveSibling(files.get(0).getFileName());
        try {
            Files.move(files.get(0), target, StandardCopyOption.REPLACE_EXISTING);
            if (!target.equals(jar)) {
                Files.deleteIfExists(jar);
            }
            Files.deleteIfExists(directory);
            downloaded.remove(key(kind, name));
            logger.info("Updated {} '{}' to {}", kind.displayName(), name, target.getFileName());
            return target;
        } catch (final IOException failure) {
            logger.error("Could not put the update of {} '{}' in place", kind.displayName(), name, failure);
            return Files.exists(jar) ? jar : target;
        }
    }

    private void loadSettings() {
        try {
            settings = settingsFile.load();
        } catch (final ConfigurateException failure) {
            logger.error("Could not read {}, using the defaults", settingsFile.path(), failure);
        }
    }

    private void reactRCP() {
        final Path rcpFolder = ExtensionKind.RCP.data(dataFolder);
        final boolean enabled;
        try {
            enabled = ConfigFile.builder(ReactRCPSettings.class)
                    .path(rcpFolder.resolve("react-rcp.yml"))
                    .defaults(UpdateService.class.getClassLoader(), language + "/react-rcp.yml")
                    .migrations(rcpMigrations(rcpFolder))
                    .logger(logger)
                    .build()
                    .load()
                    .settings()
                    .enabled();
        } catch (final ConfigurateException failure) {
            logger.error("Could not read react-rcp.yml, the React RCP is not loaded", failure);
            removeReactRCP();
            return;
        }

        if (!enabled) {
            removeReactRCP();
            statuses.put(ReleaseClient.REACT_RCP, new Status(null, UpdateChannel.RELEASE, null, "disabled"));
            return;
        }
        for (final ExtensionDescriptor descriptor : scan(ExtensionKind.RCP)) {
            if (isReactRCP(descriptor)) {
                return;
            }
        }

        final ComponentSettings component = componentSettings(ExtensionKind.RCP, "React");
        install("React RCP", ReleaseClient.REACT_RCP, REACT_RCP_PREFIX, component.effectiveChannel(),
                component.pinned(), ExtensionKind.RCP.jars(dataFolder));
    }

    /**
     * Version 2 moved {@code auto-update} and {@code channel} to {@code update/rcp/React.yml}.
     */
    private @NotNull ConfigMigrations rcpMigrations(final @NotNull Path rcpFolder) {
        return ConfigMigrations.builder()
                .step(root -> {
                })
                .step(root -> {
                    final ConfigurationNode settings = root.node("settings");
                    if ("all".equalsIgnoreCase(settings.node("channel").getString())) {
                        writePrerelease(folder.resolve("rcp").resolve("React.yml"));
                    }
                    settings.removeChild("auto-update");
                    settings.removeChild("channel");
                })
                .build();
    }

    private void writePrerelease(final @NotNull Path file) {
        if (Files.exists(file)) {
            return;
        }
        try {
            final ConfigurationNode node = ConfigFile.builder(ConfigurationNode.class)
                    .path(file)
                    .defaults(UpdateService.class.getClassLoader(), language + "/update/component.yml")
                    .logger(logger)
                    .build()
                    .loadNode();
            node.node("channel").set("prerelease");
            Files.createDirectories(file.getParent());
            YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .build()
                    .save(node);
        } catch (final IOException failure) {
            logger.warn("Could not move the React RCP channel to {}", file, failure);
        }
    }

    private void removeReactRCP() {
        for (final ExtensionDescriptor descriptor : scan(ExtensionKind.RCP)) {
            if (isReactRCP(descriptor)) {
                try {
                    Files.deleteIfExists(descriptor.jar());
                    logger.info("Removed {}, the React RCP is disabled", descriptor.jar().getFileName());
                } catch (final IOException failure) {
                    logger.error("Could not remove {}", descriptor.jar(), failure);
                }
            }
        }
    }

    private static boolean isReactRCP(final @NotNull ExtensionDescriptor descriptor) {
        return descriptor.github() != null && ReleaseClient.REACT_RCP.equalsIgnoreCase(descriptor.github().repository());
    }

    private void officialAddons() {
        final Map<String, MainConfig.OfficialAddon> addons = main.get().officialAddons();
        if (addons == null || addons.isEmpty()) {
            return;
        }
        final List<ExtensionDescriptor> present = scan(ExtensionKind.ADDON);
        for (final Map.Entry<String, MainConfig.OfficialAddon> entry : addons.entrySet()) {
            final MainConfig.OfficialAddon addon = entry.getValue();
            if (addon == null || !addon.downloadNextStart() || addon.repository() == null) {
                continue;
            }
            final boolean installed = present.stream().anyMatch(descriptor ->
                    descriptor.name().equalsIgnoreCase(entry.getKey())
                            || descriptor.github() != null && descriptor.github().repository().equalsIgnoreCase(addon.repository()));
            if (!installed) {
                install("addon " + entry.getKey(), addon.repository(), prefix(addon.repository()),
                        UpdateChannel.RELEASE, null, ExtensionKind.ADDON.jars(dataFolder));
            }
        }
    }

    private void install(
            final @NotNull String display,
            final @NotNull String repository,
            final @NotNull String prefix,
            final @NotNull UpdateChannel channel,
            final @Nullable String pinned,
            final @NotNull Path into
    ) {
        try {
            final ReleaseClient.Release release = client.release(repository, channel, pinned);
            if (release.checksums().apiVersion() != RCPAPI.VERSION) {
                statuses.put(key(repository), new Status(release.version(), channel, Instant.now(), "api-version " + release.checksums().apiVersion()));
                logger.warn("Not installing {} {}: it needs api-version {}, this plugin provides {}",
                        display, release.version(), release.checksums().apiVersion(), RCPAPI.VERSION);
                return;
            }
            final Map.Entry<String, String> jar = release.checksums().jar(prefix);
            if (jar == null) {
                statuses.put(key(repository), new Status(release.version(), channel, Instant.now(), "no jar"));
                return;
            }
            client.download(release, jar, into.resolve(jar.getKey()));
            statuses.put(key(repository), new Status(release.version(), channel, Instant.now(), "installed"));
            logger.info("Installed {} {}", display, release.version());
        } catch (final IOException failure) {
            statuses.put(key(repository), new Status(null, channel, null, "offline"));
            logger.warn("Could not download {}: {}", display, failure.getMessage());
        } catch (final ReleaseClient.VerificationException failure) {
            statuses.put(key(repository), new Status(null, channel, null, "rejected"));
            logger.error("Rejected {}: {}", display, failure.getMessage());
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized @NotNull List<Component> checkAll() {
        final List<Component> staged = new ArrayList<>();
        for (final Component component : components()) {
            if (check(component)) {
                staged.add(component);
            }
        }
        return staged;
    }

    private @NotNull List<Component> components() {
        final List<Component> components = new ArrayList<>();
        final Path pluginJar = plugin.jar();
        if (pluginJar != null) {
            components.add(new Component(UpdateDownloadedEvent.Kind.PLUGIN, "React", PLUGIN_REPOSITORY, plugin.prefix(),
                    pluginJar, plugin.updateFolder().resolve(pluginJar.getFileName()), null));
        }
        for (final ExtensionKind kind : ExtensionKind.values()) {
            for (final ExtensionDescriptor descriptor : scan(kind)) {
                final GitHubSource github = descriptor.github();
                if (github == null || !github.updatable()) {
                    continue;
                }
                components.add(new Component(
                        kind == ExtensionKind.RCP ? UpdateDownloadedEvent.Kind.RCP : UpdateDownloadedEvent.Kind.ADDON,
                        descriptor.name(),
                        github.repository(),
                        ReleaseClient.REACT_RCP.equalsIgnoreCase(github.repository()) ? REACT_RCP_PREFIX : prefix(github.repository()),
                        descriptor.jar(),
                        stagedFolder(kind, descriptor.name()),
                        kind
                ));
            }
        }
        return components;
    }

    /**
     * Checks one component and stages its release when it differs from the running jar.
     *
     * @return {@code true} when a new jar was downloaded
     */
    private boolean check(final @NotNull Component component) {
        final ComponentSettings settings = component.extension() == null
                ? componentSettings(null, component.name())
                : componentSettings(component.extension(), component.name());
        final UpdateChannel channel = settings.effectiveChannel();
        if (!settings.enabled()) {
            statuses.put(key(component.repository()), new Status(null, channel, null, "disabled"));
            return false;
        }

        try {
            final ReleaseClient.Release release = client.release(component.repository(), channel, settings.pinned());
            final Instant now = Instant.now();
            if (component.extension() != null && release.checksums().apiVersion() != RCPAPI.VERSION) {
                statuses.put(key(component.repository()), new Status(release.version(), channel, now,
                        "needs api-version " + release.checksums().apiVersion()));
                return false;
            }
            final Map.Entry<String, String> jar = release.checksums().jar(component.prefix());
            if (jar == null) {
                statuses.put(key(component.repository()), new Status(release.version(), channel, now, "no jar"));
                return false;
            }
            if (ReleaseClient.matches(component.current(), jar.getValue())) {
                statuses.put(key(component.repository()), new Status(release.version(), channel, now, "up to date"));
                return false;
            }

            final Path target = component.extension() == null
                    ? component.target()
                    : component.target().resolve(jar.getKey());
            if (ReleaseClient.matches(target, jar.getValue())) {
                statuses.put(key(component.repository()), new Status(release.version(), channel, now, "staged"));
                return false;
            }
            if (component.extension() != null) {
                clear(component.target());
            }
            client.download(release, jar, target);
            statuses.put(key(component.repository()), new Status(release.version(), channel, now, "staged"));
            logger.info("Downloaded {} {} {}", component.kind().name().toLowerCase(Locale.ROOT), component.name(), release.version());

            final UpdateDownloadedEvent event = new UpdateDownloadedEvent(component.kind(), component.name(), release.version());
            downloaded.put(component.extension() == null ? "plugin" : key(component.extension(), component.name()), event);
            notifier.accept(event);
            return true;
        } catch (final IOException failure) {
            statuses.put(key(component.repository()), new Status(null, channel, null, "offline"));
            logger.debug("Could not check {} for updates", component.repository(), failure);
        } catch (final ReleaseClient.VerificationException failure) {
            statuses.put(key(component.repository()), new Status(null, channel, null, "rejected"));
            logger.error("Rejected the update of {}: {}", component.name(), failure.getMessage());
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (final RuntimeException failure) {
            statuses.put(key(component.repository()), new Status(null, channel, null, "failed"));
            logger.error("Could not update {}", component.name(), failure);
        }
        return false;
    }

    private @NotNull ComponentSettings componentSettings(final @Nullable ExtensionKind kind, final @NotNull String name) {
        final Path file = kind == null
                ? folder.resolve("plugin.yml")
                : folder.resolve(kind == ExtensionKind.RCP ? "rcp" : "addons").resolve(name + ".yml");
        try {
            return ConfigFile.builder(ComponentSettings.class)
                    .path(file)
                    .defaults(UpdateService.class.getClassLoader(), language + "/update/component.yml")
                    .logger(logger)
                    .build()
                    .load();
        } catch (final ConfigurateException failure) {
            logger.error("Could not read {}, not updating {}", file, name, failure);
            return new ComponentSettings(false, UpdateChannel.RELEASE, "");
        }
    }

    private @NotNull List<ExtensionDescriptor> scan(final @NotNull ExtensionKind kind) {
        final List<ExtensionDescriptor> descriptors = new ArrayList<>();
        for (final Path jar : jars(kind.jars(dataFolder))) {
            try {
                descriptors.add(ExtensionDescriptor.read(kind, jar));
            } catch (final IOException | InvalidExtensionException | RuntimeException ignored) {
                // The extension manager reports unreadable jars when it loads them.
            }
        }
        return descriptors;
    }

    private static @NotNull List<Path> jars(final @NotNull Path directory) {
        final List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return jars;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jar")) {
            stream.forEach(jars::add);
        } catch (final IOException ignored) {
            // An unreadable folder holds no jars.
        }
        return jars;
    }

    private static void clear(final @NotNull Path directory) throws IOException {
        for (final Path jar : jars(directory)) {
            Files.deleteIfExists(jar);
        }
    }

    private @NotNull Path stagedFolder(final @NotNull ExtensionKind kind, final @NotNull String name) {
        return folder.resolve("staged").resolve(kind.name().toLowerCase(Locale.ROOT)).resolve(name.toLowerCase(Locale.ROOT));
    }

    private static @NotNull String prefix(final @NotNull String repository) {
        return repository.substring(repository.indexOf('/') + 1);
    }

    private static @NotNull String key(final @NotNull String repository) {
        return repository.toLowerCase(Locale.ROOT);
    }

    private static @NotNull String key(final @NotNull ExtensionKind kind, final @NotNull String name) {
        return kind.name().toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
    }

    /**
     * Stops the periodic checks.
     */
    @Override
    public void close() {
        final ScheduledExecutorService timer = this.timer;
        if (timer != null) {
            timer.shutdownNow();
        }
    }

}
