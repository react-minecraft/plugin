package com.g4vrk.react.api.config;

import com.g4vrk.react.api.config.serializer.DurationSerializer;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationSerializerTest {

    @Test
    void wrapsDurationOverflowAsSerializationFailure() throws SerializationException {

        final ConfigurationNode node = BasicConfigurationNode.root();
        node.set("9223372036854775807d");

        assertThrows(SerializationException.class, () -> DurationSerializer.INSTANCE.deserialize(Duration.class, node));
        assertThrows(SerializationException.class, () -> DurationSerializer.INSTANCE.serialize(Duration.class, Duration.ofSeconds(Long.MAX_VALUE), node));

    }

}
