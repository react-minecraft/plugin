package com.g4vrk.react.api.registry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Read-only view of values the plugin keeps by key.
 *
 * <p>Implementations are thread-safe. The plugin owns the writable side, so the
 * contents may change between two calls.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface Registry<K, V> {

    /**
     * Looks up the value under a key.
     *
     * @param key the key to look up
     * @return the value, or {@code null} when nothing is registered under the key
     */
    @Nullable V get(final @NotNull K key);

    /**
     * Checks whether a value is registered under a key.
     *
     * @param key the key to check
     * @return {@code true} when the key has a value
     */
    default boolean contains(final @NotNull K key) {
        return get(key) != null;
    }

    /**
     * Takes a snapshot of every registered value.
     *
     * @return an unmodifiable copy, safe to iterate while the registry changes
     */
    @NotNull Collection<V> values();

    /**
     * Counts the registered values.
     *
     * @return how many values are registered right now
     */
    int size();

}
