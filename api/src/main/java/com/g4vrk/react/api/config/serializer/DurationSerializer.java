package com.g4vrk.react.api.config.serializer;

import org.jetbrains.annotations.ApiStatus;

import com.g4vrk.react.api.util.TimeParser;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.time.Duration;

/**
 * Maps {@link Duration} fields to the short form configs use ({@code 2s}, {@code 30d}).
 * A malformed value fails the load with the node path in the message.
 */
@ApiStatus.AvailableSince("2.0")
public final class DurationSerializer implements TypeSerializer<Duration> {

    public static final DurationSerializer INSTANCE = new DurationSerializer();

    private DurationSerializer() {
    }

    @Override
    public Duration deserialize(final Type type, final ConfigurationNode node) throws SerializationException {
        final String raw = node.getString();
        if (raw == null) {
            throw new SerializationException(node, type, "Missing time value");
        }
        try {
            return TimeParser.parse(raw);
        } catch (final IllegalArgumentException ex) {
            throw new SerializationException(node, type, ex.getMessage());
        }
    }

    @Override
    public void serialize(final Type type, final @Nullable Duration duration, final ConfigurationNode node)
            throws SerializationException {
        node.set(duration == null ? null : TimeParser.format(duration));
    }

}
