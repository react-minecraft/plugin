package com.g4vrk.react.update;

import com.g4vrk.react.update.client.ReleaseClient;
import com.g4vrk.react.update.component.UpdateTargetScanner;
import com.g4vrk.react.update.component.UpdateTargetScanner.TargetComponent;
import com.g4vrk.react.update.config.ComponentUpdateConfig;
import com.g4vrk.react.update.config.UpdateConfig;
import com.g4vrk.react.update.stage.StagedUpdateStore;

import com.g4vrk.react.api.event.plugin.UpdateDownloadedEvent;
import com.g4vrk.react.api.rcp.RCPAPI;
import com.g4vrk.react.config.model.MainConfig;
import com.g4vrk.react.extension.descriptor.ExtensionDescriptor;
import com.g4vrk.react.extension.descriptor.ExtensionKind;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.extension.update.StagedUpdateRegistry;
import com.g4vrk.react.update.config.UpdateConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
public final class UpdateService implements StagedUpdateRegistry, AutoCloseable {

    private static final String REACT_RCP_PREFIX = "react-inference-rcp";
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(10);

    private final Path dataFolder;
    private final StagedUpdateStore staging;
    private final UpdateConfiguration configuration;
    private final Logger logger;
    private final ReleaseClient client;
    private final Plugin plugin;
    private final Supplier<MainConfig> main;
    private final Consumer<UpdateDownloadedEvent> notifier;

    private final Map<String, UpdateState> statuses = new ConcurrentHashMap<>();
    private final Map<String, UpdateDownloadedEvent> downloaded = new ConcurrentHashMap<>();
    private final UpdateReporter reporter;
    private final Object checks = new Object();

    private volatile UpdateConfig settings = new UpdateConfig(Duration.ofHours(6), true, true);
    private volatile @Nullable ScheduledExecutorService timer;
    private volatile boolean closed;

    /**
     * Where the running plugin jar is and where its update goes.
     *
     * @param jar          the running jar, or {@code null} when it cannot be found
     * @param updateFolder the server's update folder, applied on restart
     * @param prefix       the start of the release jar name for this server
     * @param version      the version of the running plugin, for the console notice
     */
    public record Plugin(
            @Nullable Path jar,
            @NotNull Path updateFolder,
            @NotNull String prefix,
            @NotNull String version,
            @NotNull String platform
    ) {

        public Plugin(
                final @Nullable Path jar,
                final @NotNull Path updateFolder,
                final @NotNull String prefix,
                final @NotNull String version
        ) {

            this(jar, updateFolder, prefix, version, "bukkit");

        }

    }

    /**
     * What the last check of a component found, for {@code /react rcps}.
     *
     * @param version   the version of the release it follows, or {@code null}
     * @param channel   the channel it follows
     * @param lastCheck when GitHub last answered, or {@code null}
     * @param state     a short word on the outcome
     */
    public record UpdateState(
            @Nullable String version,
            @NotNull UpdateChannel channel,
            @Nullable Instant lastCheck,
            @NotNull String state
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

        final Path folder = dataFolder.resolve("update");
        this.dataFolder = dataFolder;
        this.staging = new StagedUpdateStore(folder, logger);
        this.configuration = new UpdateConfiguration(dataFolder, folder, language, logger);
        this.logger = logger;
        this.client = new ReleaseClient(logger);
        this.plugin = plugin;
        this.main = main;
        this.notifier = notifier;
        this.reporter = new UpdateReporter(logger, downloaded, () -> settings);

    }

    /**
     * Runs on the extension thread before any jar is loaded: installs or removes the React RCP
     * and downloads the official addons that are missing. Update checks run asynchronously via
     * {@link #start(ExtensionManager)} so network issues do not delay server startup.
     */
    public synchronized void onStart() {

        settings = configuration.reload(settings);
        reactRCP();
        externalAddons();

    }

    /**
     * Starts the periodic checks.
     *
     * @param manager rebooted after a download when {@code auto-reboot} is on
     */
    public synchronized void start(final @NotNull ExtensionManager manager) {

        if (closed || timer != null) {
            return;
        }
        final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "React-Updates");
            thread.setDaemon(true);
            return thread;
        });
        this.timer = timer;
        if (closed) {
            timer.shutdownNow();
            return;
        }
        timer.schedule(() -> tick(manager, timer), 30L, TimeUnit.SECONDS);

    }

    /**
     * Checks everything, reboots what was downloaded and plans the next check with the interval
     * read just now, so a changed {@code interval} works without a restart.
     */
    private void tick(
            final @NotNull ExtensionManager manager,
            final @NotNull ScheduledExecutorService timer
    ) {

        try {
            settings = configuration.reload(settings);
            checkAll();
            if (settings.autoReboot()) {
                for (final UpdateDownloadedEvent event : pending()) {
                    if (event.kind() != UpdateDownloadedEvent.Kind.PLUGIN) {
                        final ExtensionKind kind = event.kind() == UpdateDownloadedEvent.Kind.RCP
                                ? ExtensionKind.RCP
                                : ExtensionKind.ADDON;
                        manager.reboot(kind, event.name());
                    }
                }
            }
        } catch (final RuntimeException failure) {
            logger.error("Could not check for updates", failure);
        }
        if (closed) {
            return;
        }
        final Duration interval = settings.interval() == null || settings.interval().compareTo(MIN_INTERVAL) < 0
                ? MIN_INTERVAL
                : settings.interval();
        try {
            timer.schedule(() -> tick(manager, timer), interval.toSeconds(), TimeUnit.SECONDS);
        } catch (final RejectedExecutionException ignored) {
            /*
             * Closed concurrently while checking for updates.
             */
        }

    }

    /**
     * The last check of a repository.
     *
     * @param repository the repository, {@code owner/name}
     * @return the status, or {@code null} when it was never checked
     */
    public @Nullable UpdateState status(final @NotNull String repository) {

        return statuses.get(repository.toLowerCase(Locale.ROOT));

    }

    /**
     * The last check of the React RCP.
     *
     * @return the status, or {@code null} when it was never checked
     */
    public @Nullable UpdateState reactRCPStatus() {

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

        return staging.apply(kind, name, jar, downloaded::remove);

    }

    private void reactRCP() {

        final boolean enabled;
        try {
            enabled = configuration.reactRcpEnabled();
        } catch (final ConfigurateException failure) {
            logger.error("Could not read react-rcp.yml, the React RCP is not loaded", failure);
            removeReactRCP();
            return;
        }

        if (!enabled) {
            removeReactRCP();
            statuses.put(ReleaseClient.REACT_RCP, new UpdateState(null, UpdateChannel.RELEASE, null, "disabled"));
            return;
        }
        for (final ExtensionDescriptor descriptor : UpdateTargetScanner.scan(dataFolder, ExtensionKind.RCP)) {
            if (UpdateTargetScanner.isReactRcp(descriptor)) {
                return;
            }
        }

        final ComponentUpdateConfig component = configuration.reactRcpUpdate();
        install(
                "React RCP",
                ReleaseClient.REACT_RCP,
                REACT_RCP_PREFIX,
                component.effectiveChannel(),
                component.pinned(),
                ExtensionKind.RCP.jars(dataFolder)
        );

    }

    private void removeReactRCP() {

        for (final ExtensionDescriptor descriptor : UpdateTargetScanner.scan(dataFolder, ExtensionKind.RCP)) {
            if (UpdateTargetScanner.isReactRcp(descriptor)) {
                try {
                    Files.deleteIfExists(descriptor.jar());
                    logger.info("Removed {}, the React RCP is disabled", descriptor.jar().getFileName());
                } catch (final IOException failure) {
                    logger.error("Could not remove {}", descriptor.jar(), failure);
                }
            }
        }

    }

    private void externalAddons() {

        final Map<String, MainConfig.ExternalAddon> addons = main.get().externalAddons();
        if (addons == null || addons.isEmpty()) {
            return;
        }
        final List<ExtensionDescriptor> present = UpdateTargetScanner.scan(dataFolder, ExtensionKind.ADDON);
        for (final Map.Entry<String, MainConfig.ExternalAddon> entry : addons.entrySet()) {
            final MainConfig.ExternalAddon addon = entry.getValue();
            if (addon == null || !addon.downloadNextStart() || addon.repository() == null) {
                continue;
            }
            if (!isAddonInstalled(present, entry.getKey(), addon.repository())) {
                install(
                        "addon " + entry.getKey(),
                        addon.repository(),
                        UpdateTargetScanner.repositoryPrefix(addon.repository()),
                        UpdateChannel.RELEASE,
                        null,
                        ExtensionKind.ADDON.jars(dataFolder)
                );
            }
        }

    }

    private static boolean isAddonInstalled(
            final @NotNull List<ExtensionDescriptor> present,
            final @NotNull String name,
            final @NotNull String repository
    ) {

        for (final ExtensionDescriptor descriptor : present) {
            if (descriptor.name().equalsIgnoreCase(name)
                    || (descriptor.github() != null && descriptor.github().repository().equalsIgnoreCase(repository))) {
                return true;
            }
        }
        return false;

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
                statuses.put(key(repository), new UpdateState(release.version(), channel, Instant.now(), "api-version " + release.checksums().apiVersion()));
                logger.warn(
                        "Not installing {} {}: it needs api-version {}, this plugin provides {}",
                        display,
                        release.version(),
                        release.checksums().apiVersion(),
                        RCPAPI.VERSION
                );
                return;
            }
            final Map.Entry<String, String> jar = release.checksums().jar(prefix, plugin.platform());
            if (jar == null) {
                statuses.put(key(repository), new UpdateState(release.version(), channel, Instant.now(), "no jar"));
                return;
            }
            client.download(release, jar, into.resolve(jar.getKey()));
            statuses.put(key(repository), new UpdateState(release.version(), channel, Instant.now(), "installed"));
            logger.info("Installed {} {}", display, release.version());
        } catch (final IOException failure) {
            statuses.put(key(repository), new UpdateState(null, channel, null, "offline"));
            logger.warn("Could not download {}: {}", display, failure.getMessage());
        } catch (final ReleaseClient.VerificationException failure) {
            statuses.put(key(repository), new UpdateState(null, channel, null, "rejected"));
            logger.error("Rejected {}: {}", display, failure.getMessage());
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
        }

    }

    private void checkAll() {

        synchronized (checks) {
            final List<TargetComponent> components = UpdateTargetScanner.collect(
                    dataFolder,
                    plugin.jar(),
                    plugin.updateFolder(),
                    plugin.prefix(),
                    plugin.version(),
                    staging
            );
            final Set<String> present = new HashSet<>();
            present.add("plugin");
            for (final TargetComponent component : components) {
                if (component.extension() != null) {
                    present.add(StagedUpdateStore.extensionKey(component.extension(), component.name()));
                }
            }
            downloaded.keySet().retainAll(present);
            staging.prune(present);
            for (final TargetComponent component : components) {
                check(component);
            }
        }

    }

    /**
     * Drops a jar staged by an earlier check, so a pin or a disabled update is not bypassed by
     * the next restart or reboot.
     */
    private void discard(
            final @NotNull TargetComponent component
    ) {

        try {
            if (component.extension() == null) {
                Files.deleteIfExists(component.target());
                downloaded.remove("plugin");
            } else {
                StagedUpdateStore.clear(component.target());
                downloaded.remove(StagedUpdateStore.extensionKey(component.extension(), component.name()));
            }
        } catch (final IOException failure) {
            logger.warn("Could not remove the staged update of {}: {}", component.name(), failure.getMessage());
        }

    }

    /**
     * Checks one component and stages its release when it differs from the running jar.
     *
     * @return {@code true} when a new jar was downloaded
     */
    private boolean check(
            final @NotNull TargetComponent component
    ) {

        final ComponentUpdateConfig componentSettings = component.extension() == ExtensionKind.RCP && ReleaseClient.REACT_RCP.equalsIgnoreCase(component.repository()) ? configuration.reactRcpUpdate() : configuration.component(component.extension(), component.name());
        final UpdateChannel channel = componentSettings.effectiveChannel();
        if (!componentSettings.enabled()) {
            statuses.put(key(component.repository()), new UpdateState(null, channel, null, "disabled"));
            discard(component);
            return false;
        }

        try {
            final ReleaseClient.Release release = client.release(component.repository(), channel, componentSettings.pinned());
            final Instant now = Instant.now();
            final String candidateVersion = release.checksums().version();
            if (componentSettings.pinned() == null
                    && !release.revokedFallback()
                    && candidateVersion != null
                    && ReleaseClient.isReleaseDowngrade(component.version(), candidateVersion)) {
                statuses.put(key(component.repository()), new UpdateState(release.version(), channel, now, "rejected"));
                logger.error(
                        "Rejected downgrade of {} from {} to {}",
                        component.name(),
                        component.version(),
                        candidateVersion
                );
                discard(component);
                return false;
            }
            if (component.extension() != null && release.checksums().apiVersion() != RCPAPI.VERSION) {
                statuses.put(
                        key(component.repository()),
                        new UpdateState(
                                release.version(),
                                channel,
                                now,
                                "needs api-version " + release.checksums().apiVersion()
                        )
                );
                reporter.needsNewerPlugin(component, release);
                return false;
            }
            final Map.Entry<String, String> jar = component.extension() == null
                    ? release.checksums().jar(component.prefix())
                    : release.checksums().jar(component.prefix(), plugin.platform());
            if (jar == null) {
                statuses.put(key(component.repository()), new UpdateState(release.version(), channel, now, "no jar"));
                return false;
            }
            if (ReleaseClient.matches(component.current(), jar.getValue())) {
                statuses.put(key(component.repository()), new UpdateState(release.version(), channel, now, "up to date"));
                discard(component);
                return false;
            }

            final Path target = component.extension() == null
                    ? component.target()
                    : component.target().resolve(jar.getKey());
            if (ReleaseClient.matches(target, jar.getValue())) {
                statuses.put(key(component.repository()), new UpdateState(release.version(), channel, now, "staged"));
                reporter.announce(component, release.version(), true);
                return false;
            }
            client.download(release, jar, target);
            if (component.extension() != null) {
                StagedUpdateStore.clearExcept(component.target(), target);
            }
            statuses.put(key(component.repository()), new UpdateState(release.version(), channel, now, "staged"));
            reporter.announce(component, release.version(), false);

            final String downloadedKey = component.extension() == null
                    ? "plugin"
                    : StagedUpdateStore.extensionKey(component.extension(), component.name());
            final UpdateDownloadedEvent event = new UpdateDownloadedEvent(component.kind(), component.name(), release.version());
            downloaded.put(downloadedKey, event);
            notifier.accept(event);
            return true;
        } catch (final IOException failure) {
            statuses.put(key(component.repository()), new UpdateState(null, channel, null, "offline"));
            logger.debug("Could not check {} for updates", component.repository(), failure);
        } catch (final ReleaseClient.VerificationException failure) {
            statuses.put(key(component.repository()), new UpdateState(null, channel, null, "rejected"));
            logger.error("Rejected the update of {}: {}", component.name(), failure.getMessage());
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (final RuntimeException failure) {
            statuses.put(key(component.repository()), new UpdateState(null, channel, null, "failed"));
            logger.error("Could not update {}", component.name(), failure);
        }
        return false;

    }

    private static @NotNull String key(
            final @NotNull String repository
    ) {

        return repository.toLowerCase(Locale.ROOT);

    }

    /**
     * Stops the periodic checks. Not synchronized on purpose: a running check holds the monitor
     * while it downloads, and shutting the server down must not wait for it.
     */
    @Override
    public void close() {

        closed = true;
        final ScheduledExecutorService timer = this.timer;
        if (timer != null) {
            timer.shutdownNow();
        }

    }

}
