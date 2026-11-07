package com.g4vrk.react.text;

import com.g4vrk.react.api.placeholder.PlaceholderRegistry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.text.Serializers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats text through TextSerializer, so a change of that library touches only this class.
 *
 * <p>Without resolvers the text goes to the universal serializer. With resolvers {@code &} codes
 * and {@code &#rrggbb} colors are turned into MiniMessage tags first and the result is parsed with
 * those resolvers.</p>
 */
public final class MiniTextFormatter implements TextFormatter {

    private static final Pattern LEGACY = Pattern.compile("[&§](?:#([0-9a-fA-F]{6})|([0-9a-fk-orA-FK-OR]))");

    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private final Serializers serializers;
    private final PlaceholderRegistry placeholders;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MiniTextFormatter(final @NotNull Serializers serializers, final @NotNull PlaceholderRegistry placeholders) {
        this.serializers = serializers;
        this.placeholders = placeholders;
    }

    @Override
    public @NotNull Component format(final @NotNull String text, final @NotNull TagResolver @NotNull ... resolvers) {
        if (resolvers.length == 0) {
            return serializers.universalSerializer().serialize(text);
        }
        return miniMessage.deserialize(legacyToTags(text), resolvers);
    }

    @Override
    public @NotNull Component format(
            final @NotNull String text,
            final @NotNull TrackedPlayer player,
            final @NotNull TagResolver @NotNull ... resolvers
    ) {
        return miniMessage.deserialize(
                legacyToTags(text),
                TagResolver.resolver(placeholders.tags(player), TagResolver.resolver(resolvers))
        );
    }

    static @NotNull String legacyToTags(final @NotNull String text) {
        if (text.indexOf('&') < 0 && text.indexOf('§') < 0) {
            return text;
        }

        final Matcher matcher = LEGACY.matcher(text);
        final StringBuilder builder = new StringBuilder(text.length() + 16);

        while (matcher.find()) {
            final String tag = matcher.group(1) != null
                    ? "#" + matcher.group(1)
                    : CODES.get(Character.toLowerCase(matcher.group(2).charAt(0)));

            matcher.appendReplacement(builder, Matcher.quoteReplacement("<" + tag + ">"));
        }

        return matcher.appendTail(builder).toString();
    }

}
