package com.g4vrk.react.api.placeholder.registry;

import com.g4vrk.react.api.placeholder.PlaceholderResolver;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.registry.write.WritableRegistry;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code react_} placeholders shared by plugin texts and PlaceholderAPI.
 *
 * <p>A key registered here works as the MiniMessage tag {@code <react_key:query>} in every
 * plugin text and, when PlaceholderAPI is installed, as {@code %react_key_query%}.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface PlaceholderRegistry extends WritableRegistry<String, PlaceholderResolver> {

    /**
     * Adds a placeholder.
     *
     * @param key      the key, made of lower-case letters, digits and {@code -}
     * @param resolver the value source
     * @throws IllegalArgumentException when the key is malformed or already taken
     */
    @Override
    void register(final @NotNull String key, final @NotNull PlaceholderResolver resolver);

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

    /**
     * Replaces {@code %key%}, {@code %key_query%}, <code>{key}</code> and <code>{key:query}</code>
     * in a text.
     *
     * <p>Use this for texts that do not go through MiniMessage, such as a config line that only
     * holds percent placeholders. A text without placeholders is returned as it is.</p>
     *
     * @param text   the text
     * @param player the player the text is formatted for
     * @return the text with every known placeholder replaced
     */
    @NotNull String apply(final @NotNull String text, final @NotNull TrackedPlayer player);

}
