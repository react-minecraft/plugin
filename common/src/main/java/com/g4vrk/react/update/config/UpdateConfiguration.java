package com.g4vrk.react.update.config;

import com.g4vrk.react.api.config.ConfigFile;
import com.g4vrk.react.api.config.ConfigMigrationChain;
import com.g4vrk.react.extension.ExtensionKind;
import com.g4vrk.react.update.ComponentUpdateConfig;
import com.g4vrk.react.update.UpdateChannel;
import com.g4vrk.react.update.UpdateConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.SerializationException;
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
    private final ConfigFile<UpdateConfig> settingsFile;

    /**
     * Creates a loader for the updater's YAML files.
     *
     * @param dataFolder the plugin data directory
     * @param folder     the {@code update} directory inside {@code dataFolder}
     * @param language   the bundled default language ({@code en} or {@code ru})
     * @param logger     the logger for config and migration messages
     */
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
        this.settingsFile = ConfigFile.builder(UpdateConfig.class)
                .path(folder.resolve("settings.yml"))
                .defaults(UpdateConfiguration.class.getClassLoader(), language + "/update/settings.yml")
                .migrations(settingsMigrations())
                .logger(logger)
                .build();

    }

    /**
     * Reloads {@code update/settings.yml}, returning {@code previous} when the file cannot be read.
     *
     * @param previous the settings to keep on failure
     * @return the loaded settings, or {@code previous}
     */
    public @NotNull UpdateConfig reload(final @NotNull UpdateConfig previous) {

        try {
            return settingsFile.load();
        } catch (final ConfigurateException failure) {
            logger.error("Could not read {}, keeping the previous update settings", settingsFile.path(), failure);
            return previous;
        }

    }

    /**
     * Reads whether the bundled React RCP is enabled in {@code .rcp/react-rcp.yml}.
     *
     * @return {@code true} when the bundled React RCP is enabled
     * @throws ConfigurateException when {@code react-rcp.yml} cannot be read or migrated
     */
    public boolean reactRcpEnabled() throws ConfigurateException {

        final Path path = ExtensionKind.RCP.data(dataFolder).resolve("react-rcp.yml");
        final ConfigFile<ReactRCPSettings> config = ConfigFile.builder(ReactRCPSettings.class)
                .path(path)
                .defaults(UpdateConfiguration.class.getClassLoader(), language + "/react-rcp.yml")
                .migrations(rcpMigrations())
                .logger(logger)
                .build();
        return config.load().settings().enabled();

    }

    /**
     * Loads the update settings for the plugin or one extension component.
     *
     * @param kind the extension kind, or {@code null} for the plugin itself
     * @param name the component name
     * @return the component settings, or disabled defaults when the file cannot be read
     */
    public @NotNull ComponentUpdateConfig component(
            final @Nullable ExtensionKind kind,
            final @NotNull String name
    ) {

        final Path file = kind == null
                ? folder.resolve("plugin.yml")
                : folder.resolve(kind == ExtensionKind.RCP ? "rcp" : "addons").resolve(name + ".yml");
        try {
            return ConfigFile.builder(ComponentUpdateConfig.class)
                    .path(file)
                    .defaults(UpdateConfiguration.class.getClassLoader(), language + "/update/component.yml")
                    .logger(logger)
                    .build()
                    .load();
        } catch (final ConfigurateException failure) {
            logger.error("Could not read {}, not updating {}", file, name, failure);
            return new ComponentUpdateConfig(false, UpdateChannel.RELEASE, "");
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
                    final boolean prerelease = "all".equalsIgnoreCase(settings.node("channel").getString());
                    final boolean disabled = !settings.node("auto-update").getBoolean(true);
                    if (prerelease || disabled) {
                        writeComponent(folder.resolve("rcp").resolve("React.yml"), prerelease, disabled);
                    }
                    settings.removeChild("auto-update");
                    settings.removeChild("channel");
                })
                .build();

    }

    private void writeComponent(
            final @NotNull Path file,
            final boolean prerelease,
            final boolean disabled
    ) throws SerializationException {

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
            if (prerelease) {
                node.node("channel").set("prerelease");
            }
            if (disabled) {
                node.node("enabled").set(false);
            }
            Files.createDirectories(file.getParent());
            YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .build()
                    .save(node);
        } catch (final IOException failure) {
            logger.error("Could not move the React RCP update settings to {}", file, failure);
            throw new SerializationException(failure);
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
