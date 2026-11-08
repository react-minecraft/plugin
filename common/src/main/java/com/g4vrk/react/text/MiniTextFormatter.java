package com.g4vrk.react.text;

import com.g4vrk.react.api.placeholder.registry.PlaceholderRegistry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.text.TagMapper;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.text.SerializerRegistry;
import com.g4vrk.text.util.Legacy2MiniMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Formats text through TextSerializer, so a change of that library touches only this class.
 *
 * <p>The library carries its own MiniMessage, which is what lets the plugin run below 1.18.2 where
 * the server has none. Because that copy lives under a relocated package, resolvers cross the
 * boundary as {@link TagMapper}s: plain strings instead of Adventure types. Placeholders of the
 * plugin go that way, so they work on every version.</p>
 *
 * <p>A caller that passes real {@link TagResolver}s asks for styling the mapper cannot express,
 * such as a click event. That path needs the MiniMessage of the server; below 1.18.2 it is skipped
 * once with a warning and the text is still formatted.</p>
 */
public final class MiniTextFormatter implements TextFormatter {

    private static final Logger LOGGER = Logger.getLogger("React");

    private static final String MINI_MESSAGE_CLASS = "net.kyori.adventure.text.minimessage.MiniMessage";

    private static final boolean SERVER_MINI_MESSAGE = present();

    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private final SerializerRegistry serializers;
    private final PlaceholderRegistry placeholders;

    public MiniTextFormatter(final @NotNull SerializerRegistry serializers, final @NotNull PlaceholderRegistry placeholders) {
        this.serializers = serializers;
        this.placeholders = placeholders;
    }

    @Override
    public @NotNull Component format(final @NotNull String text, final @NotNull TagResolver @NotNull ... resolvers) {
        if (resolvers.length == 0) {
            return this.serializers.universalSerializer().serialize(text);
        }

        if (!SERVER_MINI_MESSAGE) {
            warnOnce();
            return this.serializers.universalSerializer().serialize(text);
        }

        return ServerTags.deserialize(text, resolvers);
    }

    @Override
    public @NotNull Component format(
            final @NotNull String text,
            final @NotNull TrackedPlayer player,
            final @NotNull TagResolver @NotNull ... resolvers
    ) {
        if (resolvers.length == 0) {
            return this.serializers.universalSerializer().serialize(text, mapper(this.placeholders, player));
        }

        if (!SERVER_MINI_MESSAGE) {
            warnOnce();
            return this.serializers.universalSerializer().serialize(text, mapper(this.placeholders, player));
        }

        return ServerTags.deserialize(
                text,
                TagResolver.resolver(this.placeholders.tags(player), TagResolver.resolver(resolvers))
        );
    }

    /**
     * Formats text for a player without touching Adventure resolvers.
     *
     * @param text   the text
     * @param player the player
     * @return the component
     */
    public @NotNull Component formatWithPlaceholders(final @NotNull String text, final @NotNull TrackedPlayer player) {
        return this.serializers.universalSerializer().serialize(text, mapper(this.placeholders, player));
    }

    private static com.g4vrk.text.TagMapper mapper(
            final @NotNull PlaceholderRegistry registry,
            final @NotNull TrackedPlayer player
    ) {
        final TagMapper source = registry.mapper(player);

        return new com.g4vrk.text.TagMapper() {

            @Override
            public String map(final @NotNull String name, final @NotNull String args) {
                return source.map(name, args);
            }

            @Override
            public boolean has(final @NotNull String name) {
                return source.has(name);
            }

        };
    }

    private static boolean present() {
        try {
            Class.forName(MINI_MESSAGE_CLASS, false, MiniTextFormatter.class.getClassLoader());

            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }

    private static void warnOnce() {
        if (WARNED.compareAndSet(false, true)) {
            LOGGER.log(
                    Level.WARNING,
                    "This server has no MiniMessage: texts are formatted without the styling resolvers "
                            + "of their callers. Install Paper 1.18.2 or newer to get them back."
            );
        }
    }

    /**
     * The styling path, kept in its own class so a server without MiniMessage never loads it.
     */
    private static final class ServerTags {

        private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

        private static @NotNull Component deserialize(
                final @NotNull String text,
                final @NotNull TagResolver @NotNull ... resolvers
        ) {
            return MINI_MESSAGE.deserialize(Legacy2MiniMessage.toMarkup(text), resolvers);
        }

    }

}
