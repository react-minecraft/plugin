package com.g4vrk.react.api.placeholder;

import com.g4vrk.react.api.player.TrackedPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gives the value of one placeholder key for a player.
 *
 * <p>For {@code <react_vl:aim>} and {@code %react_vl_aim%} the key is {@code vl} and the query is
 * {@code aim}. Without a query, as in {@code <react_vl>}, the query is empty.</p>
 */
@FunctionalInterface
@ApiStatus.AvailableSince("2.0")
public interface PlaceholderResolver {

    /**
     * Resolves the value.
     *
     * @param player the player the text is formatted for
     * @param query  what follows the key, never {@code null}
     * @return the value, which may hold MiniMessage tags, or {@code null} to leave the placeholder as is
     */
    @Nullable String resolve(final @NotNull TrackedPlayer player, final @NotNull String query);

}
