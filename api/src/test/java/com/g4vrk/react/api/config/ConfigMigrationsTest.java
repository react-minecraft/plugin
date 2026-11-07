package com.g4vrk.react.api.config;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigMigrationsTest {

    @Test
    void legacyFileRunsEveryStepInOrder() throws SerializationException {
        final List<Integer> ran = new ArrayList<>();
        final ConfigMigrations chain = ConfigMigrations.builder()
                .step(root -> ran.add(1))
                .step(root -> {
                    ran.add(2);
                    root.node("renamed").set(root.node("old").getString());
                    root.node("old").raw(null);
                })
                .build();
        final ConfigurationNode root = BasicConfigurationNode.root();
        root.node("old").set("value");

        assertEquals(0, chain.apply(root));
        assertEquals(List.of(1, 2), ran);
        assertEquals("value", root.node("renamed").getString());
        assertEquals(true, root.node("old").virtual());
        assertEquals(2, ConfigMigrations.version(root));
    }

    @Test
    void stepsAlreadySeenAreSkipped() throws SerializationException {
        final List<Integer> ran = new ArrayList<>();
        final ConfigMigrations chain = ConfigMigrations.builder()
                .step(root -> ran.add(1))
                .step(root -> ran.add(2))
                .step(root -> ran.add(3))
                .build();
        final ConfigurationNode root = BasicConfigurationNode.root();
        root.node(ConfigMigrations.VERSION_KEY).set(1);

        assertEquals(1, chain.apply(root));
        assertEquals(List.of(2, 3), ran);
        assertEquals(3, ConfigMigrations.version(root));
    }

    @Test
    void currentFileIsLeftAlone() throws SerializationException {
        final ConfigurationNode root = BasicConfigurationNode.root();
        root.node(ConfigMigrations.VERSION_KEY).set(1);

        assertEquals(-1, ConfigMigrations.none().apply(root));
    }

    @Test
    void newerFileIsRejected() throws SerializationException {
        final ConfigurationNode root = BasicConfigurationNode.root();
        root.node(ConfigMigrations.VERSION_KEY).set(5);

        assertThrows(SerializationException.class, () -> ConfigMigrations.none().apply(root));
    }

    @Test
    void emptyChainIsRefused() {
        assertThrows(IllegalStateException.class, () -> ConfigMigrations.builder().build());
    }

}
