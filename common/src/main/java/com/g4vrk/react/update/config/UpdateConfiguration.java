package com.g4vrk.react.update.config;

import com.g4vrk.react.api.config.ConfigFile;
import com.g4vrk.react.api.config.ConfigMigrationChain;
import com.g4vrk.react.extension.ExtensionKind;
import com.g4vrk.react.update.ComponentSettings;
import com.g4vrk.react.update.UpdateChannel;
import com.g4vrk.react.update.UpdateSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads update settings and migrates the bundled RCP's legacy update options.
 */
public final class UpdateConfiguration {

    private final Path dataFolder;
    private final Path folder;
    private final String language;
    private final Logger logger;
    private final ConfigFile<UpdateSettings> settingsFile;

    public UpdateConfiguration(
            final @NotNull Path dataFolder,
            final @NotNull Path folder,
            final @NotNull String language,
            final @NotNull Logger logger
    ) {

        this.dataFolder = dataFolder;
        this.folder = folder;
        this.language = language;
        this.logger = logger;
        this.settingsFile = ConfigFile.builder(UpdateSettings.class).path(folder.resolve("settings.yml")).defaults(UpdateConfiguration.class.getClassLoader(), language + "/update/settings.yml").migrations(settingsMigrations()).logger(logger).build();

    }

    public @NotNull UpdateSettings reload(final @NotNull UpdateSettings previous) {

        try {
            return settingsFile.load();
        } catch (final ConfigurateException failure) {
            logger.error("Could not read {}, keeping the previous update settings", settingsFile.path(), failure);
            return previous;
        }

    }

    public boolean reactRcpEnabled() throws ConfigurateException {

        final Path path = ExtensionKind.RCP.data(dataFolder).resolve("react-rcp.yml");
        final ConfigFile<ReactRCPSettings> config = ConfigFile.builder(ReactRCPSettings.class).path(path).defaults(UpdateConfiguration.class.getClassLoader(), language + "/react-rcp.yml").migrations(rcpMigrations()).logger(logger).build();
        return config.load().settings().enabled();

    }

    public @NotNull ComponentSettings component(
            final @Nullable ExtensionKind kind,
            final @NotNull String name
    ) {

        final Path file = kind == null
                ? folder.resolve("plugin.yml")
                : folder.resolve(kind == ExtensionKind.RCP ? "rcp" : "addons").resolve(name + ".yml");
        try {
            return ConfigFile.builder(ComponentSettings.class)
                    .path(file)
                    .defaults(UpdateConfiguration.class.getClassLoader(), language + "/update/component.yml")
                    .logger(logger)
                    .build()
                    .load();
        } catch (final ConfigurateException failure) {
            logger.error("Could not read {}, not updating {}", file, name, failure);
            return new ComponentSettings(false, UpdateChannel.RELEASE, "");
        }

    }

    /**
     * Version 2 turned {@code auto-reboot} on, as updated addons and RCPs apply by default now.
     */
    private static @NotNull ConfigMigrationChain settingsMigrations() {

        return ConfigMigrationChain.builder()
                .step(root -> {
                })
                .step(root -> root.node("auto-reboot").set(true))
                .build();

    }

    /**
     * Version 2 moved {@code auto-update} and {@code channel} to {@code update/rcp/React.yml}.
     */
    private @NotNull ConfigMigrationChain rcpMigrations() {

        return ConfigMigrationChain.builder()
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
                    .defaults(UpdateConfiguration.class.getClassLoader(), language + "/update/component.yml")
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

    /**
     * The installation switch in {@code .rcp/react-rcp.yml}.
     */
    @ConfigSerializable
    private record ReactRCPSettings(Settings settings) {

        /**
         * Whether the bundled provider is enabled.
         */
        @ConfigSerializable
        record Settings(boolean enabled) {

        }

    }

}
