package com.g4vrk.react.placeholder;

import com.g4vrk.react.api.placeholder.PlaceholderRegistry;
import com.g4vrk.react.api.placeholder.PlaceholderResolver;
import com.g4vrk.react.api.player.TrackedPlayer;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Thread-safe {@link PlaceholderRegistry} whose resolvers never break the text they are used in.
 *
 * <p>A resolver that throws is logged and its placeholder is left untouched.</p>
 */
public final class SimplePlaceholderRegistry implements PlaceholderRegistry {

    public static final String PREFIX = "react_";

    private static final Pattern KEY = Pattern.compile("[a-z0-9-]+");

    private final Logger logger;
    private final Map<String, PlaceholderResolver> resolvers = new ConcurrentHashMap<>();

    public SimplePlaceholderRegistry(final @NotNull Logger logger) {
        this.logger = logger;
    }

    @Override
    public void register(final @NotNull String key, final @NotNull PlaceholderResolver resolver) {
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Placeholder key '" + key + "' must match " + KEY.pattern());
        }
        if (resolvers.putIfAbsent(key, resolver) != null) {
            throw new IllegalArgumentException("Placeholder key '" + key + "' is already registered");
        }
    }

    @Override
    public boolean unregister(final @NotNull String key) {
        return resolvers.remove(key) != null;
    }

    @Override
    public @NotNull Set<String> keys() {
        return Set.copyOf(resolvers.keySet());
    }

    @Override
    public @Nullable String resolve(
            final @NotNull TrackedPlayer player,
            final @NotNull String key,
            final @NotNull String query
    ) {
        final PlaceholderResolver resolver = resolvers.get(key);

        if (resolver == null) {
            return null;
        }

        try {
            return resolver.resolve(player, query);
        } catch (final RuntimeException exception) {
            logger.error("Placeholder '{}' failed for {}", key, player.name(), exception);
            return null;
        }
    }

    @Override
    public @NotNull TagResolver tags(final @NotNull TrackedPlayer player) {
        return new TagResolver() {
            @Override
            public @Nullable Tag resolve(
                    final @NotNull String name,
                    final @NotNull ArgumentQueue arguments,
                    final @NotNull Context context
            ) throws ParsingException {
                final StringJoiner query = new StringJoiner(":");

                while (arguments.hasNext()) {
                    query.add(arguments.pop().value());
                }

                final String value = SimplePlaceholderRegistry.this.resolve(
                        player,
                        name.substring(PREFIX.length()),
                        query.toString()
                );
                return value == null ? null : Tag.preProcessParsed(value);
            }

            @Override
            public boolean has(final @NotNull String name) {
                return name.startsWith(PREFIX) && resolvers.containsKey(name.substring(PREFIX.length()));
            }
        };
    }

}
