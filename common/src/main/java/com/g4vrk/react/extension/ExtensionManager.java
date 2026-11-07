package com.g4vrk.react.extension;

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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private final StagedUpdates updates;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "React-Extensions");
        thread.setDaemon(true);
        return thread;
    });

    private final List<LoadedExtension> loaded = new CopyOnWriteArrayList<>();

    public ExtensionManager(final @NotNull ExtensionContext context, final @NotNull StagedUpdates updates) {
        this.context = context;
        this.updates = updates;
        this.logger = context.logger();
    }

    /**
     * Loads and enables every jar, addons first.
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
            for (final ExtensionKind kind : ExtensionKind.values()) {
                loadAll(kind, name -> true, result);
            }
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
        return CompletableFuture.supplyAsync(() -> {
            final RebootResult result = new RebootResult();
            final String key = target.toLowerCase(Locale.ROOT);

            final Set<ExtensionKind> kinds = switch (key) {
                case ALL -> Set.of(ExtensionKind.values());
                case RCP -> Set.of(ExtensionKind.RCP);
                case ADDONS -> Set.of(ExtensionKind.ADDON);
                default -> null;
            };
            final NameFilter filter = kinds == null ? name -> name.equalsIgnoreCase(key) : name -> true;

            final List<LoadedExtension> targets = new ArrayList<>();
            for (final LoadedExtension extension : loaded) {
                final ExtensionDescriptor descriptor = extension.descriptor();
                if ((kinds == null || kinds.contains(descriptor.kind())) && filter.accepts(descriptor.name())) {
                    targets.add(extension);
                }
            }

            for (int index = targets.size() - 1; index >= 0; index--) {
                final LoadedExtension extension = targets.get(index);
                loaded.remove(extension);
                extension.close();
                logger.info("Disabled {}", extension);
            }

            for (final ExtensionKind kind : ExtensionKind.values()) {
                if (kinds == null || kinds.contains(kind)) {
                    loadAll(kind, filter, result);
                }
            }

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
        loaded.forEach(extension -> targets.add(extension.descriptor().name()));
        return targets;
    }

    private void loadAll(
            final @NotNull ExtensionKind kind,
            final @NotNull NameFilter filter,
            final @NotNull RebootResult result
    ) {
        for (ExtensionDescriptor descriptor : scan(kind)) {
            if (!filter.accepts(descriptor.name())) {
                continue;
            }
            if (extension(descriptor.name()) != null) {
                logger.warn("Skipping {}: an extension named '{}' is already loaded", descriptor.jar().getFileName(), descriptor.name());
                continue;
            }

            LoadedExtension extension = null;
            try {
                final Path jar = updates.apply(kind, descriptor.name(), descriptor.jar());
                if (!jar.equals(descriptor.jar())) {
                    descriptor = ExtensionDescriptor.read(kind, jar);
                }
                extension = LoadedExtension.load(context, descriptor);
                loaded.add(extension);
                extension.enable();
                result.loaded().add(descriptor.name());
                logger.info("Enabled {}", extension);
            } catch (final Throwable failure) {
                logger.error("Could not enable {} '{}'", kind.displayName(), descriptor.name(), failure);
                result.failed().add(descriptor.name());
                if (extension != null) {
                    loaded.remove(extension);
                    extension.close();
                }
            }
        }
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
     * Disables every extension in reverse load order and stops the extension thread.
     */
    @Override
    public void close() {
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
    private interface NameFilter {

        boolean accepts(final @NotNull String name);

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
