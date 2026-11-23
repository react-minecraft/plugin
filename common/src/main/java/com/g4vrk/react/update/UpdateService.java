package com.g4vrk.react.update;

import com.g4vrk.react.update.client.ReleaseClient;
import com.g4vrk.react.update.config.ComponentUpdateConfig;
import com.g4vrk.react.update.config.UpdateConfig;

import com.g4vrk.react.api.event.plugin.UpdateDownloadedEvent;
import com.g4vrk.react.api.extension.meta.GitHubSource;
import com.g4vrk.react.api.rcp.RCPAPI;
import com.g4vrk.react.config.model.MainConfig;
import com.g4vrk.react.extension.descriptor.ExtensionDescriptor;
import com.g4vrk.react.extension.descriptor.ExtensionKind;
import com.g4vrk.react.extension.ExtensionManager;
import com.g4vrk.react.extension.descriptor.InvalidExtensionException;
import com.g4vrk.react.extension.update.StagedUpdateRegistry;
import com.g4vrk.react.update.config.UpdateConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

    private static final String PLUGIN_REPOSITORY = "react-minecraft/plugin";
    private static final String REACT_RCP_PREFIX = "react-inference-rcp";
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(10);

    private final Path dataFolder;
    private final Path folder;
    private final UpdateConfiguration configuration;
    private final Logger logger;
    private final ReleaseClient client;
    private final Plugin plugin;
    private final Supplier<MainConfig> main;
    private final Consumer<UpdateDownloadedEvent> notifier;

    private final Map<String, UpdateState> statuses = new ConcurrentHashMap<>();
    private final Map<String, UpdateDownloadedEvent> downloaded = new ConcurrentHashMap<>();
    private final Map<String, String> warned = new ConcurrentHashMap<>();
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

    private record Component(
            @NotNull UpdateDownloadedEvent.Kind kind,
            @NotNull String name,
            @NotNull String repository,
            @NotNull String prefix,
            @NotNull Path current,
            @NotNull Path target,
            @Nullable ExtensionKind extension,
            @NotNull String version
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
        this.configuration = new UpdateConfiguration(dataFolder, folder, language, logger);
        this.logger = logger;
        this.client = new ReleaseClient(logger);
        this.plugin = plugin;
        this.main = main;
        this.notifier = notifier;

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
            // closed while checking
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

        final Path directory = stagedFolder(kind, name);
        final List<Path> files = jars(directory);
        if (files.size() != 1) {
            return jar;
        }
        final Path target = jar.resolveSibling(files.get(0).getFileName());
        final Path backup = jar.resolveSibling(jar.getFileName() + ".bak");
        boolean parked = false;
        try {
            if (!target.equals(jar)) {
                Files.move(jar, backup, StandardCopyOption.REPLACE_EXISTING);
                parked = true;
            }
            Files.move(files.get(0), target, StandardCopyOption.REPLACE_EXISTING);
            if (parked) {
                Files.deleteIfExists(backup);
            }
            Files.deleteIfExists(directory);
            downloaded.remove(key(kind, name));
            logger.info("Updated {} '{}' to {}", kind.displayName(), name, target.getFileName());
            return target;
        } catch (final IOException failure) {
            logger.error("Could not put the update of {} '{}' in place, keeping the old jar", kind.displayName(), name, failure);
            try {
                if (parked) {
                    Files.deleteIfExists(target);
                    Files.move(backup, jar, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (final IOException restore) {
                logger.error("Could not restore {}, it is kept as {}", jar.getFileName(), backup.getFileName(), restore);
            }
            return jar;
        }

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
        for (final ExtensionDescriptor descriptor : scan(ExtensionKind.RCP)) {
            if (isReactRCP(descriptor)) {
                return;
            }
        }

        final ComponentUpdateConfig component = configuration.component(ExtensionKind.RCP, "React");
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

        final GitHubSource source = source(descriptor);
        return source != null && ReleaseClient.REACT_RCP.equalsIgnoreCase(source.repository());

    }

    private static @Nullable GitHubSource source(final @NotNull ExtensionDescriptor descriptor) {

        final GitHubSource declared = descriptor.github();
        if (declared != null) {
            return declared;
        }
        if (descriptor.kind() == ExtensionKind.RCP && descriptor.name().equalsIgnoreCase("React")
                && descriptor.mainClass().equals("com.g4vrk.react.inference.ReactInferenceRCP")) {
            return new GitHubSource(ReleaseClient.REACT_RCP, true);
        }
        return null;

    }

    private void externalAddons() {

        final Map<String, MainConfig.ExternalAddon> addons = main.get().externalAddons();
        if (addons == null || addons.isEmpty()) {
            return;
        }
        final List<ExtensionDescriptor> present = scan(ExtensionKind.ADDON);
        for (final Map.Entry<String, MainConfig.ExternalAddon> entry : addons.entrySet()) {
            final MainConfig.ExternalAddon addon = entry.getValue();
            if (addon == null || !addon.downloadNextStart() || addon.repository() == null) {
                continue;
            }
            final boolean installed = present.stream().anyMatch(descriptor ->
                    descriptor.name().equalsIgnoreCase(entry.getKey())
                            || descriptor.github() != null && descriptor.github().repository().equalsIgnoreCase(addon.repository()));
            if (!installed) {
                install(
                        "addon " + entry.getKey(),
                        addon.repository(),
                        prefix(addon.repository()),
                        UpdateChannel.RELEASE,
                        null,
                        ExtensionKind.ADDON.jars(dataFolder)
                );
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
            final List<Component> components = components();
            final Set<String> present = new HashSet<>();
            present.add("plugin");
            for (final Component component : components) {
                if (component.extension() != null) {
                    present.add(key(component.extension(), component.name()));
                }
            }
            downloaded.keySet().retainAll(present);
            pruneStaged(present);
            for (final Component component : components) {
                check(component);
            }
        }

    }

    /**
     * Deletes staged folders belonging to extensions that are no longer installed, so reinstalling
     * one later does not apply a stale jar on top of it.
     */
    private void pruneStaged(final @NotNull Set<String> present) {

        final Path stagedRoot = folder.resolve("staged");
        for (final ExtensionKind kind : ExtensionKind.values()) {
            final Path kindDir = stagedRoot.resolve(kind.name().toLowerCase(Locale.ROOT));
            if (!Files.isDirectory(kindDir)) {
                continue;
            }
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(kindDir)) {
                for (final Path entry : entries) {
                    if (!Files.isDirectory(entry)) {
                        continue;
                    }
                    final String componentKey = key(kind, entry.getFileName().toString());
                    if (!present.contains(componentKey)) {
                        clear(entry);
                        Files.deleteIfExists(entry);
                    }
                }
            } catch (final IOException failure) {
                logger.warn("Could not clean up stale staged updates in {}", kindDir, failure);
            }
        }

    }

    /**
     * Drops a jar staged by an earlier check, so a pin or a disabled update is not bypassed by
     * the next restart or reboot.
     */
    private void discard(final @NotNull Component component) {

        try {
            if (component.extension() == null) {
                Files.deleteIfExists(component.target());
                downloaded.remove("plugin");
            } else {
                clear(component.target());
                downloaded.remove(key(component.extension(), component.name()));
            }
        } catch (final IOException failure) {
            logger.warn("Could not remove the staged update of {}: {}", component.name(), failure.getMessage());
        }

    }

    private @NotNull List<Component> components() {

        final List<Component> components = new ArrayList<>();
        final Path pluginJar = plugin.jar();
        if (pluginJar != null) {
            components.add(new Component(
                    UpdateDownloadedEvent.Kind.PLUGIN,
                    "React",
                    PLUGIN_REPOSITORY,
                    plugin.prefix(),
                    pluginJar,
                    plugin.updateFolder().resolve(pluginJar.getFileName()),
                    null,
                    plugin.version()
            ));
        }
        for (final ExtensionKind kind : ExtensionKind.values()) {
            for (final ExtensionDescriptor descriptor : scan(kind)) {
                final GitHubSource github = source(descriptor);
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
                        kind,
                        descriptor.version()
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

        final ComponentUpdateConfig componentSettings = component.extension() == null
                ? configuration.component(null, component.name())
                : configuration.component(component.extension(), component.name());
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
                    && channel == UpdateChannel.RELEASE
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
                needsNewerPlugin(component, release);
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
                announce(component, release.version(), true);
                return false;
            }
            client.download(release, jar, target);
            if (component.extension() != null) {
                clearExcept(component.target(), target);
            }
            statuses.put(key(component.repository()), new UpdateState(release.version(), channel, now, "staged"));
            announce(component, release.version(), false);

            final UpdateDownloadedEvent event = new UpdateDownloadedEvent(component.kind(), component.name(), release.version());
            downloaded.put(component.extension() == null ? "plugin" : key(component.extension(), component.name()), event);
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

    /**
     * Tells the console that a release is downloaded, what version runs now, how to apply it and
     * where to download it by hand. A reminder repeats with every check until the update is applied.
     *
     * @param component the component that was updated
     * @param version   the downloaded version
     * @param reminder  {@code true} when the jar was already downloaded by an earlier check
     */
    private void announce(
            final @NotNull Component component,
            final @NotNull String version,
            final boolean reminder
    ) {

        if (reminder && !downloaded.containsKey(component.extension() == null ? "plugin" : key(component.extension(), component.name()))) {
            return;
        }
        logger.info(
                "{} {} {} is downloaded, running {}. {}",
                reminder ? "Waiting to apply:" : "Update:",
                component.kind().name().toLowerCase(Locale.ROOT),
                component.name() + " " + version,
                component.version(),
                applyHint(component)
        );
        logger.info("Release notes and manual download: {}", releasesUrl(component));

    }

    /**
     * Warns once per version that an extension release exists but needs a newer plugin.
     *
     * @param component the extension
     * @param release   the release that needs a newer plugin
     */
    private void needsNewerPlugin(
            final @NotNull Component component,
            final @NotNull ReleaseClient.Release release
    ) {

        final String previous = warned.put(key(component.repository()), release.version());
        if (release.version().equals(previous)) {
            return;
        }
        logger.warn(
                "{} {} is released but needs api-version {}, this plugin provides {}. Update React first, then it follows: {}",
                component.name(),
                release.version(),
                release.checksums().apiVersion(),
                RCPAPI.VERSION,
                releasesUrl(component)
        );

    }

    private @NotNull String applyHint(final @NotNull Component component) {

        if (component.extension() == null) {
            return "Restart the server to apply it.";
        }
        if (settings.autoReboot()) {
            return "Auto-reboot is on, it is applied right away.";
        }
        return "Run '/react reboot " + component.name() + "' to apply it, or set auto-reboot: true in update/settings.yml.";

    }

    private static @NotNull String releasesUrl(final @NotNull Component component) {

        return "https://github.com/" + component.repository() + "/releases";

    }

    private @NotNull List<ExtensionDescriptor> scan(final @NotNull ExtensionKind kind) {

        final List<ExtensionDescriptor> descriptors = new ArrayList<>();
        for (final Path jar : jars(kind.jars(dataFolder))) {
            try {
                descriptors.add(ExtensionDescriptor.read(kind, jar));
            } catch (final IOException | InvalidExtensionException | RuntimeException ignored) {
                /*
                 * The extension manager reports unreadable jars when it loads them.
                 */
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
            /*
             * An unreadable folder holds no jars.
             */
        }
        return jars;

    }

    private static void clearExcept(
            final @NotNull Path directory,
            final @NotNull Path keep
    ) throws IOException {

        for (final Path jar : jars(directory)) {
            if (!jar.equals(keep)) {
                Files.deleteIfExists(jar);
            }
        }

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
