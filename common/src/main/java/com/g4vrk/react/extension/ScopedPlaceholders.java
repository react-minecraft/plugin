package com.g4vrk.react.extension;

import com.g4vrk.react.api.placeholder.PlaceholderResolver;
import com.g4vrk.react.api.placeholder.registry.PlaceholderRegistry;
import com.g4vrk.react.api.player.TrackedPlayer;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The placeholder registry of one extension: writes to the shared registry and removes what
 * it added on {@link #close()}.
 */
public final class ScopedPlaceholders implements PlaceholderRegistry, AutoCloseable {

    private final PlaceholderRegistry delegate;
    private final Set<String> owned = ConcurrentHashMap.newKeySet();

    public ScopedPlaceholders(final @NotNull PlaceholderRegistry delegate) {
        this.delegate = delegate;
    }

    @Override
    public void register(final @NotNull String key, final @NotNull PlaceholderResolver resolver) {
        this.delegate.register(key, resolver);
        this.owned.add(key);
    }

    @Override
    public @Nullable PlaceholderResolver unregister(final @NotNull String key) {
        return this.owned.remove(key) ? this.delegate.unregister(key) : null;
    }

    @Override
    public @Nullable PlaceholderResolver get(final @NotNull String key) {
        return this.delegate.get(key);
    }

    @Override
    public @Nullable String getKey(final @NotNull PlaceholderResolver value) {
        return this.delegate.getKey(value);
    }

    @Override
    public @NotNull Set<String> keySet() {
        return this.delegate.keySet();
    }

    @Override
    public @NotNull Set<Map.Entry<String, PlaceholderResolver>> entrySet() {
        return this.delegate.entrySet();
    }

    @Override
    public @NotNull Stream<PlaceholderResolver> stream() {
        return this.delegate.stream();
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public @NotNull Iterator<PlaceholderResolver> iterator() {
        return this.delegate.iterator();
    }

    @Override
    public @Nullable String resolve(final @NotNull TrackedPlayer player, final @NotNull String key, final @NotNull String query) {
        return this.delegate.resolve(player, key, query);
    }

    @Override
    public @NotNull TagResolver tags(final @NotNull TrackedPlayer player) {
        return this.delegate.tags(player);
    }

    @Override
    public @NotNull String apply(final @NotNull String text, final @NotNull TrackedPlayer player) {
        return this.delegate.apply(text, player);
    }

    @Override
    public void close() {
        this.owned.forEach(this.delegate::unregister);
        this.owned.clear();
    }

}
