package com.g4vrk.react.api.registry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Read-only view of the values the plugin keeps under keys.
 *
 * <p>Every registry is thread-safe and never returns {@code null} collections. The contents may
 * change between two calls, so a check followed by a lookup is not atomic; use
 * {@link #getOrThrow(Object)} when a missing value is a bug rather than a normal outcome.</p>
 *
 * <p>Iteration walks a snapshot of the values, so the registry may change while a loop runs.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface Registry<K, V> extends Iterable<V> {

    /**
     * Looks up the value under a key.
     *
     * @param key the key to look up
     * @return the value, or {@code null} when nothing is registered under the key
     */
    @Nullable V get(final @NotNull K key);

    /**
     * Looks up the value under a key and fails when it is missing.
     *
     * @param key the key to look up
     * @return the value
     * @throws NoSuchElementException when nothing is registered under the key
     */
    default @NotNull V getOrThrow(final @NotNull K key) {
        final V value = this.get(key);

        if (value == null) {
            throw new NoSuchElementException("No value registered under " + key);
        }

        return value;
    }

    /**
     * Finds the key a value is registered under.
     *
     * @param value the value to look for
     * @return the key, or {@code null} when the value is not registered
     */
    @Nullable K getKey(final @NotNull V value);

    /**
     * Finds the key a value is registered under and fails when the value is missing.
     *
     * @param value the value to look for
     * @return the key
     * @throws NoSuchElementException when the value is not registered
     */
    default @NotNull K getKeyOrThrow(final @NotNull V value) {
        final K key = this.getKey(value);

        if (key == null) {
            throw new NoSuchElementException("No key registered for " + value);
        }

        return key;
    }

    /**
     * Checks whether a value is registered under a key.
     *
     * @param key the key to check
     * @return {@code true} when the key has a value
     */
    default boolean containsKey(final @NotNull K key) {
        return this.get(key) != null;
    }

    /**
     * Lists the registered keys.
     *
     * @return an unmodifiable set that may reflect later registry changes
     */
    @NotNull Set<K> keySet();

    /**
     * Lists the registered keys with their values.
     *
     * @return an unmodifiable set of read-only entries that may reflect later registry changes
     */
    @NotNull Set<Map.Entry<K, V>> entrySet();

    /**
     * Streams the registered values.
     *
     * @return a stream over the values, which may reflect concurrent registry changes
     */
    @NotNull Stream<V> stream();

    /**
     * Counts the registered values.
     *
     * @return how many values are registered right now
     */
    int size();

    /**
     * Tells whether the registry holds nothing.
     *
     * @return {@code true} when no value is registered
     */
    default boolean isEmpty() {
        return this.size() == 0;
    }

}
