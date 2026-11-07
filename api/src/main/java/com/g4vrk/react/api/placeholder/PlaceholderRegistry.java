package com.g4vrk.react.api.placeholder;

import com.g4vrk.react.api.player.TrackedPlayer;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Holds the {@code react_} placeholders shared by plugin texts and PlaceholderAPI.
 *
 * <p>A key registered here works as the MiniMessage tag {@code <react_key:query>} in every
 * plugin text and, when PlaceholderAPI is installed, as {@code %react_key_query%}.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface PlaceholderRegistry {

    /**
     * Adds a placeholder.
     *
     * @param key      the key, made of lower-case letters, digits and {@code -}
     * @param resolver the value source
     * @throws IllegalArgumentException when the key is malformed or already taken
     */
    void register(final @NotNull String key, final @NotNull PlaceholderResolver resolver);

    /**
     * Removes a placeholder.
     *
     * @param key the key given to {@link #register(String, PlaceholderResolver)}
     * @return {@code true} when a placeholder was removed
     */
    boolean unregister(final @NotNull String key);

    /**
     * Lists the registered keys.
     *
     * @return an unmodifiable snapshot
     */
    @NotNull Set<String> keys();

    /**
     * Resolves one placeholder.
     *
     * @param player the player
     * @param key    the key
     * @param query  the query, empty when there is none
     * @return the value, or {@code null} when the key is unknown or the resolver gave nothing
     */
    @Nullable String resolve(final @NotNull TrackedPlayer player, final @NotNull String key, final @NotNull String query);

    /**
     * Builds a MiniMessage resolver for all {@code <react_key:query>} tags.
     *
     * @param player the player the text is formatted for
     * @return a resolver to pass to MiniMessage or {@link com.g4vrk.react.api.text.TextFormatter}
     */
    @NotNull TagResolver tags(final @NotNull TrackedPlayer player);

}
