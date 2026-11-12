package com.g4vrk.react.placeholder;

import com.g4vrk.react.api.placeholder.PlaceholderResolver;
import com.g4vrk.react.api.placeholder.registry.PlaceholderRegistry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.text.TagMapper;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Thread-safe {@link PlaceholderRegistry} whose resolvers never break the text they are used in.
 *
 * <p>A resolver that throws is logged and its placeholder is left untouched. The MiniMessage
 * resolver is created per player and reads the registry live, so placeholders registered after
 * a text was prepared still resolve.</p>
 */
public final class SimplePlaceholderRegistry implements PlaceholderRegistry {

    public static final String PREFIX = "react_";

    private final Logger logger;
    private final Map<String, PlaceholderResolver> resolvers = new ConcurrentHashMap<>();
    private final Map<String, PlaceholderResolver> readOnly = Collections.unmodifiableMap(this.resolvers);

    public SimplePlaceholderRegistry(final @NotNull Logger logger) {

        this.logger = logger;

    }

    @Override
    public void register(final @NotNull String key, final @NotNull PlaceholderResolver resolver) {

        if (!isValid(key)) {
            throw new IllegalArgumentException("Placeholder key '" + key + "' must match [a-z0-9-]+");
        }

        if (this.resolvers.putIfAbsent(key, resolver) != null) {
            throw new IllegalArgumentException("Placeholder key '" + key + "' is already registered");
        }

    }

    @Override
    public @Nullable PlaceholderResolver unregister(final @NotNull String key) {

        return this.resolvers.remove(key);

    }

    @Override
    public @Nullable PlaceholderResolver get(final @NotNull String key) {

        return this.resolvers.get(key);

    }

    @Override
    public @Nullable String getKey(final @NotNull PlaceholderResolver value) {

        for (final Map.Entry<String, PlaceholderResolver> entry : this.resolvers.entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }

        return null;

    }

    @Override
    public @NotNull Set<String> keySet() {

        return this.readOnly.keySet();

    }

    @Override
    public @NotNull Set<Map.Entry<String, PlaceholderResolver>> entrySet() {

        return this.readOnly.entrySet();

    }

    @Override
    public @NotNull Stream<PlaceholderResolver> stream() {

        return this.resolvers.values().stream();

    }

    @Override
    public int size() {

        return this.resolvers.size();

    }

    @Override
    public @NotNull Iterator<PlaceholderResolver> iterator() {

        return this.readOnly.values().iterator();

    }

    @Override
    public @Nullable String resolve(
            final @NotNull TrackedPlayer player,
            final @NotNull String key,
            final @NotNull String query
    ) {

        final PlaceholderResolver resolver = this.resolvers.get(key);

        if (resolver == null) {
            return null;
        }

        try {
            return resolver.resolve(player, query);
        } catch (final RuntimeException exception) {
            this.logger.error("Placeholder '{}' failed for {}", key, player.name(), exception);
            return null;
        }

    }

    @Override
    public @NotNull TagResolver tags(final @NotNull TrackedPlayer player) {

        return new Tags(player);

    }

    @Override
    public @NotNull TagMapper mapper(final @NotNull TrackedPlayer player) {

        return new Mapped(player);

    }

    @Override
    public @NotNull String apply(final @NotNull String text, final @NotNull TrackedPlayer player) {

        if (!Placeholders.mentions(text)) {
            return text;
        }

        return PlaceholderScanner.apply(text, (key, query, bracket) -> this.resolve(player, key, query));

    }

    /**
     * Checks a key against {@code [a-z0-9-]+} without a regular expression.
     *
     * @param key the key to check
     * @return {@code true} when the key is usable
     */
    static boolean isValid(final @NotNull String key) {
        final int length = key.length();

        if (length == 0) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            final char c = key.charAt(i);

            if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '-') {
                return false;
            }
        }

        return true;
    }

    /**
     * The mapper of one player: the same placeholders, answered as strings so they work with
     * either flavour of the text library.
     */
    private final class Mapped implements TagMapper {

        private final TrackedPlayer player;

        private Mapped(final @NotNull TrackedPlayer player) {

            this.player = player;

        }

        @Override
        public @Nullable String map(final @NotNull String name, final @NotNull String args) {

            if (!name.startsWith(PREFIX)) {
                return null;
            }

            return SimplePlaceholderRegistry.this.resolve(this.player, name.substring(PREFIX.length()), args);

        }

        @Override
        public boolean has(final @NotNull String name) {

            return name.startsWith(PREFIX)
                    && SimplePlaceholderRegistry.this.resolvers.containsKey(name.substring(PREFIX.length()));

        }

    }

    /**
     * The MiniMessage resolver of one player.
     */
    private final class Tags implements TagResolver {

        private final TrackedPlayer player;

        private Tags(final @NotNull TrackedPlayer player) {

            this.player = player;

        }

        @Override
        public @Nullable Tag resolve(
                final @NotNull String name,
                final @NotNull ArgumentQueue arguments,
                final @NotNull Context context
        ) throws ParsingException {

            if (!name.startsWith(PREFIX)) {
                return null;
            }

            final String value = SimplePlaceholderRegistry.this.resolve(
                    this.player,
                    name.substring(PREFIX.length()),
                    Placeholders.join(arguments)
            );

            return value == null ? null : Tag.preProcessParsed(value);

        }

        @Override
        public boolean has(final @NotNull String name) {

            return name.startsWith(PREFIX)
                    && SimplePlaceholderRegistry.this.resolvers.containsKey(name.substring(PREFIX.length()));

        }

    }

}
