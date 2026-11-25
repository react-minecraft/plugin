package com.g4vrk.react.config;

import com.g4vrk.react.api.config.ConfigFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads dotted process settings and applies them to the effective configuration after file migration.
 */
public final class EnvironmentConfig {

    private EnvironmentConfig() {
    }

    public static @Nullable String setting(final @NotNull String name) {

        final String value = System.getenv(name);
        return value == null ? System.getProperty(name) : value;

    }

    public static <T> @NotNull T load(
            final @NotNull ConfigFile<T> file,
            final @NotNull Class<T> type,
            final @NotNull String scope
    ) throws ConfigurateException {

        final Map<String, String> overrides = overrides(scope);
        if (overrides.isEmpty()) {
            return file.load();
        }
        final ConfigurationNode root = file.loadNode();
        apply(root, scope, overrides);
        try {
            final T value = root.get(type);
            if (value != null) {
                return value;
            }
        } catch (final ConfigurateException ignored) {
            /*
             * Conversion errors can contain environment values.
             */
        }
        throw new ConfigurateException("Invalid configuration overrides for react.config." + scope);

    }

    public static @NotNull ConfigurationNode node(
            final @NotNull ConfigFile<?> file,
            final @NotNull String scope
    ) throws ConfigurateException {

        final ConfigurationNode root = file.loadNode();
        apply(root, scope, overrides(scope));
        return root;

    }

    private static @NotNull Map<String, String> overrides(final @NotNull String scope) {

        final String prefix = "react.config." + scope + '.';
        final Map<String, String> result = new TreeMap<>();
        for (final String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(prefix)) {
                result.put(name.substring(prefix.length()), System.getProperty(name));
            }
        }
        for (final Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return result;

    }

    private static void apply(
            final @NotNull ConfigurationNode root,
            final @NotNull String scope,
            final @NotNull Map<String, String> overrides
    ) throws ConfigurateException {

        for (final Map.Entry<String, String> entry : overrides.entrySet()) {
            final String path = entry.getKey();
            if (!path.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*") || path.equals("config-version")) {
                throw new ConfigurateException("Expected a configuration path under react.config." + scope);
            }
            final String value = entry.getValue();
            final ConfigurationNode target = root.node((Object[]) path.split("\\."));
            if (value.startsWith("[") || value.startsWith("{")) {
                target.from(collection(value, scope, path));
            } else {
                target.set(value);
            }
        }

    }

    private static @NotNull ConfigurationNode collection(
            final @NotNull String value,
            final @NotNull String scope,
            final @NotNull String path
    ) throws ConfigurateException {

        try {
            final ConfigurationNode root = YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader("value: " + value)))
                    .build()
                    .load();
            final ConfigurationNode node = root.node("value");
            if (root.childrenMap().size() == 1 && (node.isList() || node.isMap())) {
                return node;
            }
        } catch (final ConfigurateException ignored) {
            /*
             * Parser errors can contain environment values.
             */
        }
        throw new ConfigurateException("Expected a list or map in react.config." + scope + '.' + path);

    }

}
