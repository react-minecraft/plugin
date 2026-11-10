package com.g4vrk.react.api.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.ApiStatus;
import com.g4vrk.react.api.player.TrackedPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Turns config text into components the same way the plugin does for its own messages.
 *
 * <p>Text may mix MiniMessage and {@code &} colour codes. Plugin placeholders such as
 * {@code <react_vl:aim>} resolve on their own when a player is given.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface TextFormatter {

    /**
     * Formats text without requiring MiniMessage classes from the server.
     *
     * @param text the raw text
     * @return the formatted component
     */
    @NotNull Component format(final @NotNull String text);

    /**
     * Resolves player placeholders on servers with or without native MiniMessage.
     *
     * @param text the raw text
     * @param player the placeholder subject
     * @return the formatted component
     */
    @NotNull Component format(final @NotNull String text, final @NotNull TrackedPlayer player);

    /**
     * Formats text.
     *
     * @param text      the raw text
     * @param resolvers extra placeholders, such as {@code Placeholder.unparsed("player", name)}
     * @return the formatted component
     */
    @NotNull Component format(final @NotNull String text, final @NotNull TagResolver @NotNull ... resolvers);

    /**
     * Formats text for a player, so {@code <react_key:query>} placeholders resolve too.
     *
     * @param text      the raw text
     * @param player    the player the placeholders are resolved for
     * @param resolvers extra placeholders
     * @return the formatted component
     */
    @NotNull Component format(
            final @NotNull String text,
            final @NotNull TrackedPlayer player,
            final @NotNull TagResolver @NotNull ... resolvers
    );

    /**
     * Formats mapped tags on every supported server, including Paper 1.16.5.
     *
     * @param text the raw text
     * @param mapper the tags returned as MiniMessage markup
     * @return the formatted server component
     */
    @NotNull Component formatMapped(
            final @NotNull String text,
            final @NotNull TagMapper mapper
    );

}
