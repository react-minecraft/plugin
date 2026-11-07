package com.g4vrk.react.config;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Rewrites the old {@code {name}} placeholders of a file into MiniMessage {@code <name>} tags.
 */
public final class BracePlaceholders {

    private static final Pattern BRACES = Pattern.compile("\\{([a-z][a-z0-9_-]*)}");

    private BracePlaceholders() {
    }

    /**
     * Rewrites every string in a tree.
     *
     * @param root    the file root
     * @param renamed old placeholders, braces included, that got a different tag, applied first
     * @throws SerializationException when a value cannot be written back
     */
    public static void migrate(
            final @NotNull ConfigurationNode root,
            final @NotNull Map<String, String> renamed
    ) throws SerializationException {
        rewrite(root, text -> {
            String result = text;

            for (final Map.Entry<String, String> entry : renamed.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }

            return BRACES.matcher(result).replaceAll("<$1>");
        });
    }

    /**
     * Rewrites every string in a tree with a function.
     *
     * @param node     the node to start at
     * @param rewriter the change for one string
     * @throws SerializationException when a value cannot be written back
     */
    public static void rewrite(
            final @NotNull ConfigurationNode node,
            final @NotNull UnaryOperator<String> rewriter
    ) throws SerializationException {
        if (node.isMap() || node.isList()) {
            for (final ConfigurationNode child : node.childrenList()) {
                rewrite(child, rewriter);
            }
            for (final ConfigurationNode child : node.childrenMap().values()) {
                rewrite(child, rewriter);
            }
            return;
        }

        if (node.rawScalar() instanceof String text) {
            final String rewritten = rewriter.apply(text);

            if (!rewritten.equals(text)) {
                node.set(rewritten);
            }
        }
    }

}
