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
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The plugin's own files in {@code plugins/React}, loaded together on start and on reload.
 *
 * <p>Defaults come from the language folder in the jar, e.g. {@code ru/actions.yml}.</p>
 */
public final class ReactConfigs {

    private static final Pattern HISTORY_LINK = Pattern.compile(
            "<click:run_command:'/react history \\{player} \\{page:(previous|next)}'>(.*?)</click>"
    );

    private static final ConfigMigrations AIM_AI_MIGRATIONS = ConfigMigrations.builder()
            .step(root -> {
                root.removeChild("buffer");
                root.removeChild("combat-ticks");
            })
            .build();

    private static final ConfigMigrations HISTORY_MIGRATIONS = ConfigMigrations.builder()
            .step(root -> {
            })
            .step(root -> {
                BracePlaceholders.rewrite(root, text -> HISTORY_LINK.matcher(text).replaceAll(match -> {
                    final String tag = match.group(1).equals("previous") ? "prev" : "next";
                    return Matcher.quoteReplacement("<" + tag + ">" + match.group(2) + "</" + tag + ">");
                }));
                BracePlaceholders.migrate(root, Map.of(
                        "{page:current}", "<page>",
                        "{page:max}", "<pages>",
                        "{page:previous}", "<previous-page>",
                        "{page:next}", "<next-page>",
                        "{avg-probability:colored}", "<avg-probability>",
                        "{avg-probability}", "<avg-probability-plain>",
                        "{probability:colored}", "<probability>",
                        "{probability}", "<probability-plain>"
                ));
            })
            .build();

    private static final ConfigMigrations PUNISHMENTS_MIGRATIONS = ConfigMigrations.builder()
            .step(root -> {
            })
            .step(root -> BracePlaceholders.migrate(root, Map.of()))
            .build();

    private static final ConfigMigrations MAIN_MIGRATIONS = ConfigMigrations.builder()
            .step(root -> {
            })
            .step(root -> {
                root.removeChild("alerts");
                root.removeChild("verbose");
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

        this.mainFile = file(MainConfig.class, "main-config.yml").migrations(MAIN_MIGRATIONS).build();
        this.actionsFile = file(ActionsConfig.class, "actions.yml").migrations(actionsMigrations()).build();
        this.historyFile = file(HistoryConfig.class, "history.yml").migrations(HISTORY_MIGRATIONS).build();
        this.punishmentsFile = file(ConfigurationNode.class, "punishments.yml").keepMissing()
                .migrations(PUNISHMENTS_MIGRATIONS).build();
        this.databaseFile = file(ConfigurationNode.class, "database.yml").build();
        this.aimAIFile = file(AimAIConfig.class, "checks/aim-ai.yml").migrations(AIM_AI_MIGRATIONS).build();
    }

    /**
     * Version 2 moved the alert and monitor formats from {@code main-config.yml} into action
     * lists here and renamed {@code verbose} to {@code monitor}. The formats are read from the
     * main file before its own migration drops them, so {@link #load()} loads this file first.
     */
    private @NotNull ConfigMigrations actionsMigrations() {
        return ConfigMigrations.builder()
                .step(root -> {
                })
                .step(root -> {
                    final ConfigurationNode verbose = root.node("verbose");
                    if (!verbose.virtual()) {
                        root.node("monitor").from(verbose);
                        root.removeChild("verbose");
                    }

                    final ConfigurationNode legacy = legacyMain();
                    moveFormat(legacy.node("alerts", "format"), root.node("alerts", "alert"));
                    moveFormat(legacy.node("verbose", "format"), root.node("monitor", "message"));
                })
                .step(root -> BracePlaceholders.migrate(root, Map.of()))
                .build();
    }

    private @NotNull ConfigurationNode legacyMain() {
        final Path path = directory.resolve("main-config.yml");
        try {
            return YamlConfigurationLoader.builder().path(path).build().load();
        } catch (final ConfigurateException ex) {
            logger.warn("Could not read the old alert formats from {}", path.getFileName(), ex);
            return BasicConfigurationNode.root();
        }
    }

    private static void moveFormat(
            final @NotNull ConfigurationNode format,
            final @NotNull ConfigurationNode actions
    ) throws SerializationException {
        final String text = format.getString();
        if (text != null) {
            actions.setList(String.class, List.of("[msg] " + text.replace("{verbose}", "{details}")));
        }
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
        final ActionsConfig actions = actionsFile.load();
        final MainConfig main = mainFile.load();
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
