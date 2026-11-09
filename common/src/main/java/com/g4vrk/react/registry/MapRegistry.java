package com.g4vrk.react.registry;

import com.g4vrk.react.api.registry.write.WritableRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Thread-safe {@link WritableRegistry} backed by one concurrent map.
 *
 * <p>Reads copy nothing: {@link #keySet()}, {@link #entrySet()}, {@link #stream()} and
 * {@link #iterator()} are unmodifiable, weakly consistent views of the map.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
public class MapRegistry<K, V> implements WritableRegistry<K, V> {

    protected final Map<K, V> entries = new ConcurrentHashMap<>();
    private final Map<K, V> readOnly = Collections.unmodifiableMap(this.entries);

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
        return this.readOnly.keySet();
    }

    @Override
    public @NotNull Set<Map.Entry<K, V>> entrySet() {
        return this.readOnly.entrySet();
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
        return this.readOnly.values().iterator();
    }

}
