package com.g4vrk.react.update.stage;

import com.g4vrk.react.extension.descriptor.ExtensionKind;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages extension jars staged in {@code update/staged} and applies them atomically on load.
 */
public final class StagedUpdateStore {

    private final Path stagedRoot;
    private final Logger logger;

    public StagedUpdateStore(
            final @NotNull Path updateFolder,
            final @NotNull Logger logger
    ) {

        this.stagedRoot = updateFolder.resolve("staged");
        this.logger = logger;

    }

    public @NotNull Path stagedFolder(
            final @NotNull ExtensionKind kind,
            final @NotNull String name
    ) {

        return stagedRoot
                .resolve(kind.name().toLowerCase(Locale.ROOT))
                .resolve(name.toLowerCase(Locale.ROOT));

    }

    public @NotNull Path apply(
            final @NotNull ExtensionKind kind,
            final @NotNull String name,
            final @NotNull Path jar,
            final @NotNull Consumer<String> onApplied
    ) {

        final Path directory = stagedFolder(kind, name);
        final List<Path> files = jars(directory);
        if (files.size() != 1) {
            return jar;
        }
        final Path target = jar.resolveSibling(files.get(0).getFileName());
        final Path backup = jar.resolveSibling(jar.getFileName() + ".bak");
        boolean parked = false;
        boolean updated = false;
        try {
            if (!target.equals(jar)) {
                Files.move(jar, backup, StandardCopyOption.REPLACE_EXISTING);
                parked = true;
            }
            Files.move(files.get(0), target, StandardCopyOption.REPLACE_EXISTING);
            updated = true;
        } catch (final IOException failure) {
            logger.error("Could not put the update of {} '{}' in place, keeping the old jar", kind.displayName(), name, failure);
            if (!updated && parked) {
                try {
                    Files.deleteIfExists(target);
                    Files.move(backup, jar, StandardCopyOption.REPLACE_EXISTING);
                } catch (final IOException restore) {
                    logger.error("Could not restore {}, it is kept as {}", jar.getFileName(), backup.getFileName(), restore);
                }
            }
            return jar;
        }
        if (parked) {
            try {
                Files.deleteIfExists(backup);
            } catch (final IOException cleanupFailure) {
                logger.warn("Could not delete backup jar {}", backup.getFileName(), cleanupFailure);
            }
        }
        try {
            Files.deleteIfExists(directory);
        } catch (final IOException cleanupFailure) {
            logger.warn("Could not delete staged directory {}", directory, cleanupFailure);
        }
        onApplied.accept(extensionKey(kind, name));
        logger.info("Updated {} '{}' to {}", kind.displayName(), name, target.getFileName());
        return target;

    }

    /**
     * Deletes staged folders belonging to extensions that are no longer installed, so reinstalling
     * one later does not apply a stale jar on top of it.
     */
    public void prune(
            final @NotNull Set<String> present
    ) {

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
                    final String componentKey = extensionKey(kind, entry.getFileName().toString());
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

    public static void clearExcept(
            final @NotNull Path directory,
            final @NotNull Path keep
    ) throws IOException {

        for (final Path jar : jars(directory)) {
            if (!jar.equals(keep)) {
                Files.deleteIfExists(jar);
            }
        }

    }

    public static void clear(
            final @NotNull Path directory
    ) throws IOException {

        for (final Path jar : jars(directory)) {
            Files.deleteIfExists(jar);
        }

    }

    public static @NotNull List<Path> jars(
            final @NotNull Path directory
    ) {

        final List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return jars;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jar")) {
            for (final Path entry : stream) {
                jars.add(entry);
            }
        } catch (final IOException ignored) {
            /*
             * An unreadable folder holds no jars.
             */
        }
        return jars;

    }

    public static @NotNull String extensionKey(
            final @NotNull ExtensionKind kind,
            final @NotNull String name
    ) {

        return kind.name().toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);

    }

}
