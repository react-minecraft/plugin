package com.g4vrk.react.config.migration;

import com.g4vrk.react.api.config.migration.ConfigMigrationChain;
import com.g4vrk.react.config.BracePlaceholderResolver;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versioned Configurate migration chains for React's built-in YAML configuration files.
 */
public final class ReactConfigMigration {

    private static final Pattern HISTORY_LINK = Pattern.compile(
            "<click:run_command:'/react history \\{player} \\{page:(previous|next)}'>(.*?)</click>"
    );

    private static final String[][] OFFICIAL_ADDONS = {
            {"hologram", "hologram-addon", "true"},
            {"menu", "menu-addon", "true"},
            {"punish", "punish-addon", "true"}
    };

    private static final ConfigMigrationChain AIM_AI = ConfigMigrationChain.builder()
            .step(root -> {
                root.removeChild("buffer");
                root.removeChild("combat-ticks");
            })
            .step(root -> {
                if (!root.node("debug").isList()) {
                    root.removeChild("debug");
                }
            })
            .build();

    private static final ConfigMigrationChain PUNISHMENTS = ConfigMigrationChain.builder()
            .step(root -> {
            })
            .step(root -> BracePlaceholderResolver.migrate(root, Map.of()))
            .step(root -> {
                for (final ConfigurationNode group : root.node("punishments").childrenMap().values()) {
                    final ConfigurationNode actions = group.node("actions");
                    for (final Map.Entry<Object, ? extends ConfigurationNode> entry : actions.childrenMap().entrySet()) {
                        final String key = String.valueOf(entry.getKey());
                        group.node(key.indexOf(';') == -1 ? "at" : "every", key).from(entry.getValue());
                    }
                    group.removeChild("actions");
                }
            })
            .build();

    private static final ConfigMigrationChain MAIN = ConfigMigrationChain.builder()
            .step(root -> {
            })
            .step(root -> {
                root.removeChild("alerts");
                root.removeChild("verbose");
            })
            .step(root -> {
                final ConfigurationNode addons = root.node("official-addons");
                if (!addons.virtual()) {
                    return;
                }
                for (final String[] addon : OFFICIAL_ADDONS) {
                    addons.node(addon[0], "repository").set("react-minecraft/" + addon[1]);
                    addons.node(addon[0], "download-next-start").set(Boolean.parseBoolean(addon[2]));
                }
            })
            /*
             * collect-addon is private and no longer an official addon.
             */
            .step(root -> root.node("official-addons").removeChild("collect"))
            .step(root -> {
                final ConfigurationNode legacy = root.node("official-addons");
                if (!legacy.virtual()) {
                    root.node("external-addons").mergeFrom(legacy);
                    root.removeChild("official-addons");
                }
            })
            .build();

    private ReactConfigMigration() {

        throw new UnsupportedOperationException("Utility class");

    }

    public static @NotNull ConfigMigrationChain aimAi() {

        return AIM_AI;

    }

    public static @NotNull ConfigMigrationChain punishments() {

        return PUNISHMENTS;

    }

    public static @NotNull ConfigMigrationChain main() {

        return MAIN;

    }

    public static @NotNull ConfigMigrationChain history(
            final @NotNull String language
    ) {

        return ConfigMigrationChain.builder()
                .step(root -> {
                })
                .step(root -> {
                    BracePlaceholderResolver.rewrite(root, text -> HISTORY_LINK.matcher(text).replaceAll(match -> {
                        final String tag = match.group(1).equals("previous") ? "prev" : "next";
                        return Matcher.quoteReplacement("<" + tag + ">" + match.group(2) + "</" + tag + ">");
                    }));
                    BracePlaceholderResolver.migrate(root, Map.of(
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
                .step(root -> addProvenanceLine(root.node("history", "inference", "format", "entry-format"), language))
                .build();

    }

    public static @NotNull ConfigMigrationChain actions(
            final @NotNull Path directory,
            final @NotNull Logger logger
    ) {

        return ConfigMigrationChain.builder()
                .step(root -> {
                })
                .step(root -> {
                    final ConfigurationNode verbose = root.node("verbose");
                    if (!verbose.virtual()) {
                        root.node("monitor").from(verbose);
                        root.removeChild("verbose");
                    }

                    final ConfigurationNode legacy = legacyMain(directory, logger);
                    moveFormat(legacy.node("alerts", "format"), root.node("alerts", "alert"));
                    moveFormat(legacy.node("verbose", "format"), root.node("monitor", "message"));
                })
                .step(root -> BracePlaceholderResolver.migrate(root, Map.of()))
                .build();

    }

    private static void addProvenanceLine(
            final @NotNull ConfigurationNode entryFormat,
            final @NotNull String language
    ) throws SerializationException {

        if (!entryFormat.isList()) {
            return;
        }
        boolean hasModel = false;
        boolean hasVersion = false;
        boolean hasThreshold = false;
        boolean hasMode = false;
        boolean hasViolationsBefore = false;
        boolean hasViolationsAfter = false;
        for (final ConfigurationNode line : entryFormat.childrenList()) {
            final String text = line.getString("");
            hasModel |= text.contains("<model>");
            hasVersion |= text.contains("<version>");
            hasThreshold |= text.contains("<threshold>");
            hasMode |= text.contains("<mode>");
            hasViolationsBefore |= text.contains("<vl-before>");
            hasViolationsAfter |= text.contains("<vl-after>");
        }
        if (hasModel && hasVersion && hasThreshold && hasMode && hasViolationsBefore && hasViolationsAfter) {
            return;
        }

        final String line = language.equalsIgnoreCase("ru")
                ? " <dark_gray>[<model> | <version>] порог: <threshold> · режим: <mode> · VL <vl-before> → <vl-after></dark_gray>"
                : " <dark_gray>[<model> | <version>] threshold: <threshold> · mode: <mode> · VL <vl-before> → <vl-after></dark_gray>";
        entryFormat.appendListNode().set(line);

    }

    private static @NotNull ConfigurationNode legacyMain(
            final @NotNull Path directory,
            final @NotNull Logger logger
    ) {

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

}
