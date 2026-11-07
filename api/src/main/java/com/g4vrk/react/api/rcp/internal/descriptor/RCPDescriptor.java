package com.g4vrk.react.api.rcp.internal.descriptor;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import com.g4vrk.react.api.Descriptor;
import com.g4vrk.react.api.rcp.RCPAPI;
import com.g4vrk.react.api.rcp.exception.InvalidRCPException;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class RCPDescriptor implements Descriptor<RCPMetadata, Path> {

    @Override
    public @NotNull RCPMetadata read(@NotNull final Path source) throws IOException {

        final ConfigurationNode config = YamlConfigurationLoader.builder().path(source).build().load();

        final String name = requireNonNull(config.node("name").getString(), "RCP name cannot be null!");
        final String version = requireNonNull(config.node("version").getString(), "RCP version cannot be null!");
        final String mainClass = requireNonNull(config.node("mainClass").getString(), "Main class cannot be null!");
        final int apiVersion = config.node("api-version").getInt(0);

        if (apiVersion != RCPAPI.VERSION) {
            throw new InvalidRCPException(
                    "RCP '" + name + "' is built for api-version " + apiVersion + ", this plugin provides " + RCPAPI.VERSION
            );
        }

        return new RCPMetadata(
                name,
                version,
                mainClass,
                apiVersion
        );
    }
}
