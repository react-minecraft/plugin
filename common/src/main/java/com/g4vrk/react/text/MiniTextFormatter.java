package com.g4vrk.react.text;

import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.text.Serializers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Formats text through TextSerializer, so a change of that library touches only this class.
 *
 * <p>Without resolvers the text goes to the universal serializer. With resolvers {@code &} codes
 * are converted to MiniMessage first and the result is parsed with those resolvers.</p>
 */
public final class MiniTextFormatter implements TextFormatter {

    private final Serializers serializers;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MiniTextFormatter(final @NotNull Serializers serializers) {
        this.serializers = serializers;
    }

    @Override
    public @NotNull Component format(final @NotNull String text, final @NotNull TagResolver @NotNull ... resolvers) {
        if (resolvers.length == 0) {
            return serializers.universalSerializer().serialize(text);
        }
        return miniMessage.deserialize(serializers.legacyConverter().serialize(text), resolvers);
    }

}
