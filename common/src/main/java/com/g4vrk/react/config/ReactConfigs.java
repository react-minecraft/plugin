package com.g4vrk.react.config;

import com.g4vrk.react.api.config.ConfigFile;
import com.g4vrk.react.api.config.ConfigMigrations;
import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.config.model.AimAIConfig;
import com.g4vrk.react.config.model.HistoryConfig;
import com.g4vrk.react.config.model.MainConfig;
import com.g4vrk.react.storage.config.DatabaseType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

import java.nio.file.Path;
import java.util.Map;

/**
 * The plugin's own files in {@code plugins/React}, loaded together on start and on reload.
 *
 * <p>Defaults come from the language folder in the jar, e.g. {@code ru/actions.yml}.</p>
 */
public final class ReactConfigs {

    private static final ConfigMigrations AIM_AI_MIGRATIONS = ConfigMigrations.builder()
            .step(root -> {
                root.removeChild("buffer");
                root.removeChild("combat-ticks");
            })
            .build();

    private final Path directory;
    private final String language;
    private final Logger logger;

    private final ConfigFile<MainConfig> mainFile;
    private final ConfigFile<ActionsConfig> actionsFile;
    private final ConfigFile<HistoryConfig> historyFile;
    private final ConfigFile<ConfigurationNode> punishmentsFile;
    private final ConfigFile<ConfigurationNode> databaseFile;
    private final ConfigFile<AimAIConfig> aimAIFile;

    private volatile MainConfig main;
    private volatile ActionsConfig actions;
    private volatile HistoryConfig history;
    private volatile ConfigurationNode punishments;
    private volatile ConfigurationNode database;
    private volatile ConfigurationNode databaseType;
    private volatile Map<String, Object> checks;

    public ReactConfigs(
            final @NotNull Path directory,
            final @NotNull String language,
            final @NotNull Logger logger
    ) {
        this.directory = directory;
        this.language = language;
        this.logger = logger;

        this.mainFile = file(MainConfig.class, "main-config.yml").build();
        this.actionsFile = file(ActionsConfig.class, "actions.yml").build();
        this.historyFile = file(HistoryConfig.class, "history.yml").build();
        this.punishmentsFile = file(ConfigurationNode.class, "punishments.yml").keepMissing().build();
        this.databaseFile = file(ConfigurationNode.class, "database.yml").build();
        this.aimAIFile = file(AimAIConfig.class, "checks/aim-ai.yml").migrations(AIM_AI_MIGRATIONS).build();
    }

    private <T> ConfigFile.@NotNull Builder<T> file(final @NotNull Class<T> type, final @NotNull String name) {
        return ConfigFile.builder(type)
                .path(directory.resolve(name))
                .defaults(ReactConfigs.class.getClassLoader(), language + "/" + name)
                .logger(logger);
    }

    /**
     * Reads every file. Nothing is replaced unless all of them load.
     */
    public void load() throws ConfigurateException {
        final MainConfig main = mainFile.load();
        final ActionsConfig actions = actionsFile.load();
        final HistoryConfig history = historyFile.load();
        final ConfigurationNode punishments = punishmentsFile.loadNode();
        final ConfigurationNode database = databaseFile.loadNode();
        final String typeFile = "database/" + DatabaseType.parse(database.node("type").getString("sqlite")).configName();
        final ConfigurationNode databaseType = file(ConfigurationNode.class, typeFile).build().loadNode();
        final AimAIConfig aimAI = aimAIFile.load();

        this.main = main;
        this.actions = actions;
        this.history = history;
        this.punishments = punishments;
        this.database = database;
        this.databaseType = databaseType;
        this.checks = Map.of("aim-ai", aimAI);
    }

    public @NotNull MainConfig main() {
        return main;
    }

    public @NotNull ActionsConfig actions() {
        return actions;
    }

    public @NotNull HistoryConfig history() {
        return history;
    }

    public @NotNull ConfigurationNode punishments() {
        return punishments;
    }

    public @NotNull ConfigurationNode database() {
        return database;
    }

    /**
     * The file of the database picked by {@code type} in {@code database.yml}.
     */
    public @NotNull ConfigurationNode databaseType() {
        return databaseType;
    }

    /**
     * The settings of a check by its {@code configId}.
     */
    public @NotNull Object check(final @NotNull String configId) {
        final Object config = checks.get(configId);
        if (config == null) {
            throw new IllegalStateException("Missing configuration for check '" + configId + "'");
        }
        return config;
    }

}
