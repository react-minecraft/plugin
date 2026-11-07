package com.g4vrk.react.registry;

import com.g4vrk.react.api.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link Registry} the plugin can change.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface WritableRegistry<K, V> extends Registry<K, V> {

    /**
     * Puts a value under a key, replacing the previous one.
     *
     * @param key   the key
     * @param value the value
     * @return the replaced value, or {@code null} when the key was free
     */
    @Nullable V register(final @NotNull K key, final @NotNull V value);

    /**
     * Removes the value under a key.
     *
     * @param key the key
     * @return the removed value, or {@code null} when nothing was registered
     */
    @Nullable V unregister(final @NotNull K key);

    /**
     * Removes every value.
     */
    void clear();

}
