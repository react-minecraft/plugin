package com.g4vrk.react.extension;

import com.g4vrk.react.api.placeholder.PlaceholderRegistry;
import com.g4vrk.react.api.placeholder.PlaceholderResolver;
import com.g4vrk.react.api.player.TrackedPlayer;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
        delegate.register(key, resolver);
        owned.add(key);
    }

    @Override
    public boolean unregister(final @NotNull String key) {
        return owned.remove(key) && delegate.unregister(key);
    }

    @Override
    public @NotNull Set<String> keys() {
        return delegate.keys();
    }

    @Override
    public @Nullable String resolve(final @NotNull TrackedPlayer player, final @NotNull String key, final @NotNull String query) {
        return delegate.resolve(player, key, query);
    }

    @Override
    public @NotNull TagResolver tags(final @NotNull TrackedPlayer player) {
        return delegate.tags(player);
    }

    @Override
    public void close() {
        owned.forEach(delegate::unregister);
        owned.clear();
    }

}
