package com.g4vrk.react.api.rcp.internal.environment;

import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * How the plugin builds the environment of each RCP it loads, and takes back what the
 * RCP registered once it is disabled.
 */
@ApiStatus.Internal
public interface RCPEnvironmentFactory {

    @NotNull RCPEnvironment create(
            @NotNull RCPMetadata metadata,
            @NotNull Path directory,
            @NotNull Logger logger
    );

    void release(@NotNull RCPEnvironment environment);

}
