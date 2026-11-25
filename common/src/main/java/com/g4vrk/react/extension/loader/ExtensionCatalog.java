package com.g4vrk.react.extension.loader;

import com.g4vrk.react.api.extension.meta.ExtensionId;
import com.g4vrk.react.extension.descriptor.ExtensionDescriptor;
import com.g4vrk.react.extension.descriptor.ExtensionKind;
import com.g4vrk.react.extension.descriptor.ExtensionPlan;
import com.g4vrk.react.extension.descriptor.InvalidExtensionException;
import com.g4vrk.react.extension.update.StagedUpdateRegistry;
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
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Discovers installed extension jars, reads bootstrap plans, and selects load order.
 */
public final class ExtensionCatalog {

    private final Path dataFolder;
    private final StagedUpdateRegistry updates;
    private final Logger logger;

    public ExtensionCatalog(
            final @NotNull Path dataFolder,
            final @NotNull StagedUpdateRegistry updates,
            final @NotNull Logger logger
    ) {

        this.dataFolder = dataFolder;
        this.updates = updates;
        this.logger = logger;

    }

    /**
     * An installed JAR paired with its bootstrap plan.
     */
    public record Candidate(
            @NotNull ExtensionDescriptor descriptor,
            @NotNull ExtensionPlan plan
    ) {

    }

    public @NotNull Map<ExtensionId, Candidate> discover(
            final @NotNull Set<ExtensionId> alreadyLoadedIds,
            final @NotNull List<LoadedExtension> loaded,
            final @NotNull Predicate<ExtensionDescriptor> filter,
            final @NotNull BooleanSupplier stopping,
            final @NotNull List<String> failedNames
    ) {

        final Map<ExtensionId, Candidate> pending = new LinkedHashMap<>();
        final Set<ExtensionId> duplicates = new HashSet<>();

        for (final ExtensionKind kind : ExtensionKind.values()) {
            for (final ExtensionDescriptor installed : scan(kind)) {
                if (stopping.getAsBoolean()) {
                    return pending;
                }
                if (!filter.test(installed) || isLoaded(loaded, kind, installed.name())) {
                    continue;
                }
                try {
                    final Path jar = updates.apply(kind, installed.name(), installed.jar());
                    final ExtensionDescriptor descriptor = jar.equals(installed.jar())
                            ? installed
                            : ExtensionDescriptor.read(kind, jar);
                    final ExtensionPlan plan = ExtensionPlan.read(descriptor);
                    final Candidate candidate = new Candidate(descriptor, plan);
                    if (alreadyLoadedIds.contains(plan.id()) || duplicates.contains(plan.id())) {
                        logger.error("Could not enable {}: Duplicate extension ID {}", plan.id(), plan.id());
                        failedNames.add(descriptor.name());
                        continue;
                    }
                    final Candidate previous = pending.putIfAbsent(plan.id(), candidate);
                    if (previous != null) {
                        duplicates.add(plan.id());
                        pending.remove(plan.id());
                        logger.error("Could not enable {}: Duplicate extension ID {}", previous.plan().id(), plan.id());
                        failedNames.add(previous.descriptor().name());
                        logger.error("Could not enable {}: Duplicate extension ID {}", candidate.plan().id(), plan.id());
                        failedNames.add(candidate.descriptor().name());
                    }
                } catch (final Exception | LinkageError | ServiceConfigurationError failure) {
                    logger.error("Could not bootstrap {} '{}'", kind.displayName(), installed.name(), failure);
                    failedNames.add(installed.name());
                }
            }
        }
        return pending;

    }

    public static @Nullable Candidate nextReady(
            final @NotNull Map<ExtensionId, Candidate> pending
    ) {

        Candidate fallbackReady = null;
        for (final Candidate candidate : pending.values()) {
            final Map<ExtensionId, ExtensionPlan.Dependency> dependencies = candidate.plan().dependencies();
            if (!hasPendingRequired(dependencies, pending)) {
                if (fallbackReady == null) {
                    fallbackReady = candidate;
                }
                if (!hasPendingAny(dependencies, pending)) {
                    return candidate;
                }
            }
        }
        return fallbackReady;

    }

    private @NotNull Collection<ExtensionDescriptor> scan(
            final @NotNull ExtensionKind kind
    ) {

        final Path folder = kind.jars(dataFolder);
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

    private static boolean isLoaded(
            final @NotNull List<LoadedExtension> loaded,
            final @NotNull ExtensionKind kind,
            final @NotNull String name
    ) {

        for (final LoadedExtension extension : loaded) {
            if (extension.descriptor().kind() == kind && extension.descriptor().name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;

    }

    private static boolean hasPendingRequired(
            final @NotNull Map<ExtensionId, ExtensionPlan.Dependency> dependencies,
            final @NotNull Map<ExtensionId, Candidate> pending
    ) {

        for (final Map.Entry<ExtensionId, ExtensionPlan.Dependency> entry : dependencies.entrySet()) {
            if (entry.getValue().required() && pending.containsKey(entry.getKey())) {
                return true;
            }
        }
        return false;

    }

    private static boolean hasPendingAny(
            final @NotNull Map<ExtensionId, ExtensionPlan.Dependency> dependencies,
            final @NotNull Map<ExtensionId, Candidate> pending
    ) {

        for (final ExtensionId id : dependencies.keySet()) {
            if (pending.containsKey(id)) {
                return true;
            }
        }
        return false;

    }

}
