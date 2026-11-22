package com.g4vrk.react.extension;

import com.g4vrk.react.extension.descriptor.ExtensionDescriptor;
import com.g4vrk.react.extension.descriptor.ExtensionKind;
import com.g4vrk.react.extension.descriptor.ExtensionPlan;
import com.g4vrk.react.extension.descriptor.InvalidExtensionException;
import com.g4vrk.react.extension.environment.ExtensionContext;
import com.g4vrk.react.extension.loader.LoadedExtension;
import com.g4vrk.react.extension.update.StagedUpdateRegistry;

import com.g4vrk.react.api.extension.ExtensionId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ServiceConfigurationError;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads, enables, reboots and closes addons and RCPs.
 *
 * <p>Every lifecycle call runs on one thread, {@code React-Extensions}, so an extension never
 * sees two of them at once. Disabling an extension removes everything it registered through its
 * environment and closes its class loader.</p>
 */
public final class ExtensionManager implements AutoCloseable {

    /**
     * Reboot target that means every extension.
     */
    public static final String ALL = "all";

    /**
     * Reboot target that means every RCP.
     */
    public static final String RCP = "rcp";

    /**
     * Reboot target that means every addon.
     */
    public static final String ADDONS = "addons";

    private static final long CLOSE_TIMEOUT_SECONDS = 15;

    private final ExtensionContext context;
    private final StagedUpdateRegistry updates;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "React-Extensions");
        thread.setDaemon(true);
        return thread;
    });

    private final List<LoadedExtension> loaded = new CopyOnWriteArrayList<>();
    private final AtomicBoolean stopping = new AtomicBoolean();

    public ExtensionManager(final @NotNull ExtensionContext context, final @NotNull StagedUpdateRegistry updates) {

        this.context = context;
        this.updates = updates;
        this.logger = context.logger();

    }

    /**
     * Loads and enables every JAR in dependency order.
     *
     * @param beforeLoad runs on the extension thread before the jars are read, for downloads
     * @return done once every extension is enabled or failed
     */
    public @NotNull CompletableFuture<Void> start(final @NotNull Runnable beforeLoad) {

        return CompletableFuture.runAsync(() -> {
            try {
                beforeLoad.run();
            } catch (final RuntimeException failure) {
                logger.error("Could not check extension updates", failure);
            }
            final RebootResult result = new RebootResult();
            loadAll(descriptor -> true, result);
        }, executor);

    }

    /**
     * Reboots extensions: disables them, puts staged updates in place and loads their jars again.
     * New jars of the targeted kinds are loaded too.
     *
     * @param target {@link #ALL}, {@link #RCP}, {@link #ADDONS} or an extension name
     * @return what was unloaded, loaded and what failed
     */
    public @NotNull CompletableFuture<RebootResult> reboot(final @NotNull String target) {

        final String key = target.toLowerCase(Locale.ROOT);
        final Set<ExtensionKind> kinds = switch (key) {
            case ALL -> Set.of(ExtensionKind.values());
            case RCP -> Set.of(ExtensionKind.RCP);
            case ADDONS -> Set.of(ExtensionKind.ADDON);
            default -> null;
        };
        return reboot(
                extension -> kinds == null
                        ? extension.descriptor().name().equalsIgnoreCase(key) || extension.plan().id().toString().equals(key)
                        : kinds.contains(extension.descriptor().kind()),
                descriptor -> kinds == null
                        ? descriptor.name().equalsIgnoreCase(key)
                        : kinds.contains(descriptor.kind())
        );

    }

    /**
     * Reboots one extension identified by its kind and name, without matching group keywords
     * ({@link #ALL}, {@link #RCP}, {@link #ADDONS}) or colliding across kinds.
     *
     * @param kind the extension kind
     * @param name the extension name, any case
     * @return what was unloaded, loaded and what failed
     */
    public @NotNull CompletableFuture<RebootResult> reboot(
            final @NotNull ExtensionKind kind,
            final @NotNull String name
    ) {

        return reboot(
                extension -> extension.descriptor().kind() == kind && extension.descriptor().name().equalsIgnoreCase(name),
                descriptor -> descriptor.kind() == kind && descriptor.name().equalsIgnoreCase(name)
        );

    }

    private @NotNull CompletableFuture<RebootResult> reboot(
            final @NotNull ExtensionSelector selector,
            final @NotNull DescriptorFilter reloadFilter
    ) {

        return CompletableFuture.supplyAsync(() -> {
            final RebootResult result = new RebootResult();
            final List<LoadedExtension> targets = new ArrayList<>();
            final Set<ExtensionId> affected = new HashSet<>();
            for (final LoadedExtension extension : loaded) {
                if (selector.matches(extension) || extension.services().dependencyIds().stream().anyMatch(affected::contains)) {
                    targets.add(extension);
                    affected.add(extension.plan().id());
                }
            }

            final List<LoadedExtension> prepared = new ArrayList<>();
            for (int index = targets.size() - 1; index >= 0; index--) {
                final LoadedExtension extension = targets.get(index);
                CompletableFuture<Void> preparation = null;
                try {
                    prepared.add(extension);
                    preparation = extension.prepareDisable();
                    preparation.get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (final Throwable failure) {
                    if (preparation != null) {
                        preparation.cancel(false);
                    }
                    if (failure instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    for (final LoadedExtension previous : prepared) {
                        try {
                            previous.cancelDisablePreparation();
                        } catch (final Throwable cancellationFailure) {
                            logger.error("Could not cancel reboot preparation for {}", previous, cancellationFailure);
                        }
                    }
                    logger.error("Could not prepare {} for reboot; leaving extensions enabled", extension, failure);
                    result.failed().add(extension.descriptor().name());
                    return result;
                }
            }

            for (int index = targets.size() - 1; index >= 0; index--) {
                final LoadedExtension extension = targets.get(index);
                loaded.remove(extension);
                extension.close();
                logger.info("Disabled {}", extension);
            }

            final Set<ExtensionDescriptor> stopped = new HashSet<>();
            targets.forEach(extension -> stopped.add(extension.descriptor()));
            loadAll(descriptor -> reloadFilter.accepts(descriptor)
                    || stopped.stream().anyMatch(old -> old.kind() == descriptor.kind() && old.name().equalsIgnoreCase(descriptor.name())), result);

            for (final LoadedExtension extension : targets) {
                final String name = extension.descriptor().name();
                if (!result.loaded().contains(name) && !result.failed().contains(name)) {
                    result.unloaded().add(name);
                }
            }
            return result;
        }, executor);

    }

    /**
     * Calls {@code onReload} of every enabled RCP.
     *
     * @return done once every RCP reloaded
     */
    public @NotNull CompletableFuture<Void> reload() {

        return CompletableFuture.runAsync(() -> {
            for (final LoadedExtension extension : loaded) {
                try {
                    extension.reload();
                } catch (final Throwable failure) {
                    extension.environment().logger().error("{} could not reload", extension, failure);
                }
            }
        }, executor);

    }

    /**
     * The loaded extensions, in load order.
     *
     * @return a copy of the list
     */
    public @NotNull List<LoadedExtension> extensions() {

        return List.copyOf(loaded);

    }

    /**
     * The loaded extension with a name.
     *
     * @param name the name, any case
     * @return the extension, or {@code null}
     */
    public @Nullable LoadedExtension extension(final @NotNull String name) {

        for (final LoadedExtension extension : loaded) {
            if (extension.descriptor().name().equalsIgnoreCase(name)) {
                return extension;
            }
        }
        return null;

    }

    /**
     * What {@code /react reboot} accepts: the group targets and every loaded name.
     *
     * @return the targets
     */
    public @NotNull List<String> targets() {

        final List<String> targets = new ArrayList<>(List.of(ALL, RCP, ADDONS));
        loaded.forEach(extension -> {
            targets.add(extension.descriptor().name());
            targets.add(extension.plan().id().toString());
        });
        return targets;

    }

    private void loadAll(
            final @NotNull DescriptorFilter filter,
            final @NotNull RebootResult result
    ) {

        if (stopping.get()) {
            return;
        }
        final Map<ExtensionId, LoadedExtension> available = new LinkedHashMap<>();
        loaded.forEach(extension -> available.put(extension.plan().id(), extension));
        final Map<ExtensionId, Candidate> pending = new LinkedHashMap<>();
        final Set<ExtensionId> duplicates = new HashSet<>();
        for (final ExtensionKind kind : ExtensionKind.values()) {
            for (final ExtensionDescriptor installed : scan(kind)) {
                if (stopping.get()) {
                    return;
                }
                if (!filter.accepts(installed) || loaded.stream().anyMatch(extension ->
                        extension.descriptor().kind() == kind && extension.descriptor().name().equalsIgnoreCase(installed.name()))) {
                    continue;
                }
                try {
                    final Path jar = updates.apply(kind, installed.name(), installed.jar());
                    final ExtensionDescriptor descriptor = jar.equals(installed.jar()) ? installed : ExtensionDescriptor.read(kind, jar);
                    final ExtensionPlan plan = ExtensionPlan.read(descriptor);
                    final Candidate candidate = new Candidate(descriptor, plan);
                    if (available.containsKey(plan.id()) || duplicates.contains(plan.id())) {
                        fail(candidate, "Duplicate extension ID " + plan.id(), result);
                        continue;
                    }
                    final Candidate previous = pending.putIfAbsent(plan.id(), candidate);
                    if (previous != null) {
                        duplicates.add(plan.id());
                        pending.remove(plan.id());
                        fail(previous, "Duplicate extension ID " + plan.id(), result);
                        fail(candidate, "Duplicate extension ID " + plan.id(), result);
                    }
                } catch (final Exception | LinkageError | ServiceConfigurationError failure) {
                    logger.error("Could not bootstrap {} '{}'", kind.displayName(), installed.name(), failure);
                    result.failed().add(installed.name());
                }
            }
        }

        while (!pending.isEmpty()) {
            if (stopping.get()) {
                return;
            }
            Candidate ready = null;
            for (final Candidate candidate : pending.values()) {
                final Map<ExtensionId, ExtensionPlan.Dependency> dependencies = candidate.plan().dependencies();
                if (dependencies.entrySet().stream().noneMatch(entry -> entry.getValue().required() && pending.containsKey(entry.getKey()))) {
                    if (ready == null) {
                        ready = candidate;
                    }
                    if (dependencies.keySet().stream().noneMatch(pending::containsKey)) {
                        ready = candidate;
                        break;
                    }
                }
            }
            if (ready == null) {
                final Set<ExtensionId> blocked = pending.keySet();
                pending.values().forEach(candidate -> fail(candidate, "Blocked by a required dependency cycle among " + blocked, result));
                return;
            }
            pending.remove(ready.plan().id());
            enable(ready, available, pending, result);
        }

    }

    private void enable(
            final @NotNull Candidate candidate,
            final @NotNull Map<ExtensionId, LoadedExtension> available,
            final @NotNull Map<ExtensionId, Candidate> pending,
            final @NotNull RebootResult result
    ) {

        final Map<ExtensionId, LoadedExtension> dependencies = new LinkedHashMap<>();
        for (final Map.Entry<ExtensionId, ExtensionPlan.Dependency> entry : candidate.plan().dependencies().entrySet()) {
            final LoadedExtension provider = available.get(entry.getKey());
            if (provider != null && provider.plan().apiVersion() == entry.getValue().apiVersion()) {
                dependencies.put(entry.getKey(), provider);
            } else if (entry.getValue().required()) {
                fail(candidate, "Requires " + entry.getKey() + " API " + entry.getValue().apiVersion()
                        + (provider == null ? " (unavailable)" : " (found API " + provider.plan().apiVersion() + ')'), result);
                return;
            } else if (pending.containsKey(entry.getKey())) {
                logger.warn("Skipping optional dependency {} -> {} to avoid blocking activation", candidate.plan().id(), entry.getKey());
            }
        }

        LoadedExtension extension = null;
        try {
            extension = LoadedExtension.load(context, candidate.descriptor(), candidate.plan(), dependencies);
            if (stopping.get() || !context.platform().enabled()) {
                extension.close();
                return;
            }
            extension.enable();
            loaded.add(extension);
            available.put(candidate.plan().id(), extension);
            result.loaded().add(candidate.descriptor().name());
            logger.info("Enabled {}", extension);
        } catch (final Throwable failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Could not enable {}", candidate.plan().id(), failure);
            result.failed().add(candidate.descriptor().name());
            if (extension != null) {
                extension.close();
            }
        }

    }

    private void fail(
            final @NotNull Candidate candidate,
            final @NotNull String reason,
            final @NotNull RebootResult result
    ) {

        logger.error("Could not enable {}: {}", candidate.plan().id(), reason);
        result.failed().add(candidate.descriptor().name());

    }

    /**
     * An installed JAR with its bootstrap declarations.
     */
    private record Candidate(ExtensionDescriptor descriptor, ExtensionPlan plan) {
    }

    private @NotNull Collection<ExtensionDescriptor> scan(final @NotNull ExtensionKind kind) {

        final Path folder = kind.jars(context.dataFolder());
        final List<ExtensionDescriptor> descriptors = new ArrayList<>();
        final Set<String> names = new HashSet<>();

        try {
            Files.createDirectories(folder);
        } catch (final IOException failure) {
            logger.error("Could not create {}", folder, failure);
            return descriptors;
        }

        try (DirectoryStream<Path> jars = Files.newDirectoryStream(folder, "*.jar")) {
            for (final Path jar : jars) {
                try {
                    final ExtensionDescriptor descriptor = ExtensionDescriptor.read(kind, jar);
                    if (names.add(descriptor.name().toLowerCase(Locale.ROOT))) {
                        descriptors.add(descriptor);
                    } else {
                        logger.warn("Skipping {}: another jar is also named '{}'", jar.getFileName(), descriptor.name());
                    }
                } catch (final InvalidExtensionException failure) {
                    logger.error(failure.getMessage());
                } catch (final IOException | RuntimeException failure) {
                    logger.error("Could not read {}", jar.getFileName(), failure);
                }
            }
        } catch (final IOException failure) {
            logger.error("Could not list {}", folder, failure);
        }

        descriptors.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
        return descriptors;

    }

    /**
     * Prevents further extension activation as soon as shutdown begins.
     */
    public void stopLoading() {

        stopping.set(true);

    }

    /**
     * Disables every extension in reverse load order and stops the extension thread.
     */
    @Override
    public void close() {

        stopLoading();
        final CompletableFuture<Void> closing = CompletableFuture.runAsync(() -> {
            final List<LoadedExtension> extensions = new ArrayList<>(loaded);
            loaded.clear();
            for (int index = extensions.size() - 1; index >= 0; index--) {
                extensions.get(index).close();
            }
        }, executor);

        try {
            closing.get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final TimeoutException failure) {
            logger.error("Extensions did not stop within {} seconds", CLOSE_TIMEOUT_SECONDS);
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (final Exception failure) {
            logger.error("Could not stop extensions", failure);
        } finally {
            executor.shutdownNow();
        }

    }

    @FunctionalInterface
    private interface DescriptorFilter {

        boolean accepts(final @NotNull ExtensionDescriptor descriptor);

    }

    @FunctionalInterface
    private interface ExtensionSelector {

        boolean matches(final @NotNull LoadedExtension extension);

    }

    /**
     * What a reboot did, by extension name.
     *
     * @param unloaded extensions that were disabled and have no jar any more
     * @param loaded   extensions that were enabled
     * @param failed   extensions that could not be enabled
     */
    public record RebootResult(
            @NotNull List<String> unloaded,
            @NotNull List<String> loaded,
            @NotNull List<String> failed
    ) {

        RebootResult() {

            this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        }

        /**
         * Whether nothing matched the target.
         *
         * @return {@code true} when all three lists are empty
         */
        public boolean empty() {

            return unloaded.isEmpty() && loaded.isEmpty() && failed.isEmpty();

        }

    }

}
