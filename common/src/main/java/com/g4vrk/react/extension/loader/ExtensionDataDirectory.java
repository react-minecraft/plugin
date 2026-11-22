package com.g4vrk.react.extension.loader;

import com.g4vrk.react.extension.descriptor.ExtensionDescriptor;
import com.g4vrk.react.extension.environment.ExtensionContext;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Resolves persistent extension directories and moves data from legacy display names.
 */
public final class ExtensionDataDirectory {

    private static final Map<String, String> ADDON_DIRECTORIES = Map.of("com.g4vrk.hologramAddon.ReactHologramAddon", "hologram-addon", "com.g4vrk.menuAddon.ReactMenuAddon", "menu-addon", "com.g4vrk.punishAddon.ReactPunishAddon", "punish-addon", "com.g4vrk.reactdataset.ReactDatasetAddon", "collect-addon", "com.example.addon.ExampleAddon", "example-addon");

    private ExtensionDataDirectory() {
    }

    public static @NotNull Path resolve(            final @NotNull ExtensionContext context,
            final @NotNull ExtensionDescriptor descriptor
    ) throws IOException {

        final Path root = descriptor.kind().data(context.dataFolder());
        final String name = switch (descriptor.kind()) {
            case ADDON -> ADDON_DIRECTORIES.getOrDefault(descriptor.mainClass(), descriptor.name());
            case RCP -> descriptor.mainClass().equals("com.g4vrk.react.inference.ReactInferenceRCP") ? "react-inference-rcp" : descriptor.name();
        };
        final Path directory = root.resolve(name);
        final Path legacy = root.resolve(descriptor.name());
        if (!directory.equals(legacy) && Files.isDirectory(legacy)) {
            if (Files.notExists(directory)) {
                Files.move(legacy, directory);
            } else {
                context.logger().warn("Both data directories exist for {}. Using {}", descriptor.name(), directory);
            }
        }
        Files.createDirectories(directory);
        return directory;

    }

}
