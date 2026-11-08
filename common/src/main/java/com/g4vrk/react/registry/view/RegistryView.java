package com.g4vrk.react.registry.view;

import com.g4vrk.react.api.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Answers a {@link Registry} of a wider value type from a registry of a narrower one.
 *
 * <p>An implementation that keeps {@code TrackedPlayerImpl} can hand addons a
 * {@code Registry<UUID, TrackedPlayer>} by extending this class and overriding nothing but the
 * methods that are its own. Nothing is copied until a caller asks for a collection.</p>
 *
 * @param <K> key type
 * @param <T> value type this view answers
 * @param <V> value type the delegate holds, a subtype of {@code T}
 */
public abstract class RegistryView<K, T, V extends T> implements Registry<K, T> {

    protected final Registry<K, V> delegate;

    protected RegistryView(final @NotNull Registry<K, V> delegate) {
        this.delegate = delegate;
    }

    @Override
    public @Nullable T get(final @NotNull K key) {
        return this.delegate.get(key);
    }

    @Override
    public @Nullable K getKey(final @NotNull T value) {
        for (final Map.Entry<K, V> entry : this.delegate.entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }

        return null;
    }

    @Override
    public @NotNull Set<K> keySet() {
        return this.delegate.keySet();
    }

    @Override
    public @NotNull Set<Map.Entry<K, T>> entrySet() {
        final Set<Map.Entry<K, V>> source = this.delegate.entrySet();
        final Set<Map.Entry<K, T>> out = new LinkedHashSet<>(source.size() * 2);

        for (final Map.Entry<K, V> entry : source) {
            out.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
        }

        return Collections.unmodifiableSet(out);
    }

    @Override
    public @NotNull Stream<T> stream() {
        return this.delegate.stream().map(value -> value);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull Iterator<T> iterator() {
        return (Iterator<T>) this.delegate.iterator();
    }

}
