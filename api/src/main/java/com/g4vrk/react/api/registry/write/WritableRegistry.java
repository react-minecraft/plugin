package com.g4vrk.react.api.registry.write;

import com.g4vrk.react.api.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link Registry} that can be changed.
 *
 * <p>Extensions get one of these for the data they contribute to the plugin. Anything registered
 * here belongs to the extension and is removed when the extension is disabled.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface WritableRegistry<K, V> extends Registry<K, V> {

    /**
     * Puts a value under a key, replacing the previous one.
     *
     * @param key   the key
     * @param value the value
     */
    void register(final @NotNull K key, final @NotNull V value);

    /**
     * Removes the value under a key.
     *
     * @param key the key
     * @return the removed value, or {@code null} when nothing was registered
     */
    @Nullable V unregister(final @NotNull K key);

}
