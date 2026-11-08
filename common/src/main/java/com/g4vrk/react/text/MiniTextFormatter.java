package com.g4vrk.react.text;

import com.g4vrk.react.api.placeholder.registry.PlaceholderRegistry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.text.SerializerRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Formats text through TextSerializer, so a change of that library touches only this class.
 *
 * <p>TextSerializer turns {@code &} codes into MiniMessage markup itself, so this formatter only
 * joins the plugin's placeholders with the resolvers of the caller and hands everything over.
 * There is no separate conversion pass here any more: a text with resolvers goes through the same
 * serializer as a text without them.</p>
 */
public final class MiniTextFormatter implements TextFormatter {

    private final SerializerRegistry serializers;
    private final PlaceholderRegistry placeholders;

    public MiniTextFormatter(final @NotNull SerializerRegistry serializers, final @NotNull PlaceholderRegistry placeholders) {
        this.serializers = serializers;
        this.placeholders = placeholders;
    }

    @Override
    public @NotNull Component format(final @NotNull String text, final @NotNull TagResolver @NotNull ... resolvers) {
        return this.serializers.universalSerializer().serialize(text, resolvers);
    }

    @Override
    public @NotNull Component format(
            final @NotNull String text,
            final @NotNull TrackedPlayer player,
            final @NotNull TagResolver @NotNull ... resolvers
    ) {
        return this.serializers.universalSerializer().serialize(
                text,
                resolvers.length == 0
                        ? this.placeholders.tags(player)
                        : TagResolver.resolver(this.placeholders.tags(player), TagResolver.resolver(resolvers))
        );
    }

}
