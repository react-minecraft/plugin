package com.g4vrk.react.api.config;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * One step of a config file's migration chain. It receives the root node of a file
 * that is exactly one version older than the step and rewrites it in place.
 */
@ApiStatus.AvailableSince("2.0")
@FunctionalInterface
public interface ConfigMigration {

    void migrate(@NotNull ConfigurationNode root) throws SerializationException;

}
