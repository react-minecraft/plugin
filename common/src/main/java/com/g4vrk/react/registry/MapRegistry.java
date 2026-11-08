package com.g4vrk.react.registry;

import com.g4vrk.react.api.registry.write.WritableRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Thread-safe {@link WritableRegistry} backed by one concurrent map.
 *
 * <p>Lookups, {@link #keySet()} and {@link #entrySet()} copy nothing on the read path;
 * {@link #stream()} is weakly consistent, and {@link #iterator()} walks a snapshot because the
 * {@link Iterable} contract has no way to report a concurrent change.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
public class MapRegistry<K, V> implements WritableRegistry<K, V> {

    protected final Map<K, V> entries = new ConcurrentHashMap<>();

    @Override
    public @Nullable V get(final @NotNull K key) {
        return this.entries.get(key);
    }

    @Override
    public @Nullable K getKey(final @NotNull V value) {
        for (final Map.Entry<K, V> entry : this.entries.entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }

        return null;
    }

    @Override
    public @NotNull Set<K> keySet() {
        return Set.copyOf(this.entries.keySet());
    }

    @Override
    public @NotNull Set<Map.Entry<K, V>> entrySet() {
        return Set.copyOf(this.entries.entrySet());
    }

    @Override
    public @NotNull Stream<V> stream() {
        return this.entries.values().stream();
    }

    @Override
    public int size() {
        return this.entries.size();
    }

    @Override
    public void register(final @NotNull K key, final @NotNull V value) {
        this.entries.put(key, value);
    }

    @Override
    public @Nullable V unregister(final @NotNull K key) {
        return this.entries.remove(key);
    }

    /**
     * Removes every value.
     */
    public void clear() {
        this.entries.clear();
    }

    @Override
    public @NotNull Iterator<V> iterator() {
        return List.copyOf(this.entries.values()).iterator();
    }

}
