package com.g4vrk.react.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe {@link WritableRegistry} backed by a concurrent map.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class MapRegistry<K, V> implements WritableRegistry<K, V> {

    protected final Map<K, V> entries = new ConcurrentHashMap<>();

    @Override
    public @Nullable V get(final @NotNull K key) {
        return entries.get(key);
    }

    @Override
    public @NotNull Collection<V> values() {
        return List.copyOf(entries.values());
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public @Nullable V register(final @NotNull K key, final @NotNull V value) {
        return entries.put(key, value);
    }

    @Override
    public @Nullable V unregister(final @NotNull K key) {
        return entries.remove(key);
    }

    @Override
    public void clear() {
        entries.clear();
    }

}
