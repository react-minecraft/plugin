package com.g4vrk.react.api.config;

import com.g4vrk.react.api.config.serializer.DurationSerializer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.util.NamingSchemes;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One YAML file mapped onto a {@code @ConfigSerializable} type.
 *
 * <p>The bundled default is copied as-is the first time, so its comments reach the server
 * owner. Keys missing from the owner's file are filled from the default in memory only.
 * The file is rewritten just when a migration changes its structure; the previous file is
 * kept next to it as {@code <name>.v<old version>.bak}, because the YAML writer drops
 * comments. A migration that only bumps the version edits that one line.</p>
 *
 * <p>Field names map to dashed keys: {@code checkBedrockPlayers} reads
 * {@code check-bedrock-players}. {@link Duration} fields take the short form {@code 2s}.</p>
 *
 * @param <T> the mapped type
 */
public final class ConfigFile<T> {

    private static final int INDENT = 2;

    private static final Pattern VERSION_LINE = Pattern.compile(
            "^" + Pattern.quote(ConfigMigrations.VERSION_KEY) + ":.*$", Pattern.MULTILINE
    );

    private static final ConfigurationOptions OPTIONS = ConfigurationOptions.defaults()
            .serializers(builder -> builder
                    .register(Duration.class, DurationSerializer.INSTANCE)
                    .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
                            .defaultNamingScheme(NamingSchemes.LOWER_CASE_DASHED)
                            .build()));

    private final Path path;
    private final Class<T> type;
    private final Supplier<InputStream> defaults;
    private final ConfigMigrations migrations;
    private final boolean fillMissing;
    private final Logger logger;

    private ConfigFile(final @NotNull Builder<T> builder) {
        this.path = Objects.requireNonNull(builder.path, "path");
        this.type = builder.type;
        this.defaults = Objects.requireNonNull(builder.defaults, "defaults");
        this.migrations = builder.migrations;
        this.fillMissing = builder.fillMissing;
        this.logger = Objects.requireNonNull(builder.logger, "logger");
    }

    public static <T> @NotNull Builder<T> builder(final @NotNull Class<T> type) {
        return new Builder<>(type);
    }

    public @NotNull Path path() {
        return path;
    }

    /**
     * Copies the default if the file is missing, migrates the file and maps it onto {@code T}.
     *
     * @throws ConfigurateException when the file cannot be read or does not fit {@code T}
     */
    public @NotNull T load() throws ConfigurateException {
        final CommentedConfigurationNode root = loadNode();
        final T value = root.get(type);
        if (value == null) {
            throw new ConfigurateException(root, "Could not map " + path.getFileName() + " onto " + type.getSimpleName());
        }
        return value;
    }

    /**
     * Same as {@link #load()}, but hands back the raw tree.
     *
     * <p>For files whose keys are the owner's own names, such as punishment groups.</p>
     */
    public @NotNull CommentedConfigurationNode loadNode() throws ConfigurateException {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.toAbsolutePath().getParent());
                try (InputStream in = openDefaults()) {
                    Files.copy(in, path);
                }
            }

            final CommentedConfigurationNode root = loader(path).load();
            final CommentedConfigurationNode before = root.copy();
            final int previous = migrations.apply(root);

            if (previous != -1) {
                persistMigration(root, before, previous);
            }

            if (fillMissing) {
                root.mergeFrom(loadDefaults());
            }
            return root;
        } catch (final ConfigurateException ex) {
            throw ex;
        } catch (final IOException ex) {
            throw new ConfigurateException("Could not read " + path, ex);
        }
    }

    private void persistMigration(
            final @NotNull CommentedConfigurationNode migrated,
            final @NotNull CommentedConfigurationNode before,
            final int previous
    ) throws IOException {
        before.node(ConfigMigrations.VERSION_KEY).set(migrated.node(ConfigMigrations.VERSION_KEY).raw());

        if (before.equals(migrated)) {
            stampVersion(migrations.latest());
            return;
        }

        final Path backup = path.resolveSibling(path.getFileName() + ".v" + previous + ".bak");
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        loader(path).save(migrated);
        logger.warn("Migrated {} from version {} to {}. The old file is kept as {}",
                path.getFileName(), previous, migrations.latest(), backup.getFileName());
    }

    private void stampVersion(final int version) throws IOException {
        final String text = Files.readString(path, StandardCharsets.UTF_8);
        final String line = ConfigMigrations.VERSION_KEY + ": " + version;
        final Matcher matcher = VERSION_LINE.matcher(text);

        final String updated;
        if (matcher.find()) {
            updated = matcher.replaceFirst(line);
        } else {
            final String separator = text.contains("\r\n") ? "\r\n" : "\n";
            final String body = text.isEmpty() || text.endsWith("\n") ? text : text + separator;
            updated = body + separator + line + separator;
        }
        Files.writeString(path, updated, StandardCharsets.UTF_8);
    }

    private @NotNull CommentedConfigurationNode loadDefaults() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openDefaults(), StandardCharsets.UTF_8))) {
            return YamlConfigurationLoader.builder()
                    .defaultOptions(OPTIONS)
                    .source(() -> reader)
                    .build()
                    .load();
        }
    }

    private @NotNull InputStream openDefaults() throws IOException {
        final InputStream in = defaults.get();
        if (in == null) {
            throw new IOException("Bundled default for " + path.getFileName() + " is missing");
        }
        return in;
    }

    private static @NotNull YamlConfigurationLoader loader(final @NotNull Path path) {
        return YamlConfigurationLoader.builder()
                .path(path)
                .defaultOptions(OPTIONS)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(INDENT)
                .build();
    }

    public static final class Builder<T> {

        private final Class<T> type;
        private Path path;
        private Supplier<InputStream> defaults;
        private ConfigMigrations migrations = ConfigMigrations.none();
        private boolean fillMissing = true;
        private Logger logger;

        private Builder(final @NotNull Class<T> type) {
            this.type = type;
        }

        public @NotNull Builder<T> path(final @NotNull Path path) {
            this.path = path;
            return this;
        }

        /**
         * The bundled file with its comments.
         *
         * <p>It is copied on first start and fills keys the owner's file lacks.</p>
         */
        public @NotNull Builder<T> defaults(final @NotNull Supplier<InputStream> defaults) {
            this.defaults = defaults;
            return this;
        }

        /**
         * Reads the bundled file from {@code loader}.
         *
         * @param resource the resource path, e.g. {@code ru/actions.yml}
         */
        public @NotNull Builder<T> defaults(final @NotNull ClassLoader loader, final @NotNull String resource) {
            return defaults(() -> loader.getResourceAsStream(resource));
        }

        public @NotNull Builder<T> migrations(final @NotNull ConfigMigrations migrations) {
            this.migrations = migrations;
            return this;
        }

        /**
         * Stops filling missing keys from the bundled file.
         *
         * <p>Use it for files whose sections are the owner's own list, such as punishment
         * groups, where an entry the owner removed must stay removed.</p>
         */
        public @NotNull Builder<T> keepMissing() {
            this.fillMissing = false;
            return this;
        }

        public @NotNull Builder<T> logger(final @NotNull Logger logger) {
            this.logger = logger;
            return this;
        }

        public @NotNull ConfigFile<T> build() {
            return new ConfigFile<>(this);
        }

    }

}
