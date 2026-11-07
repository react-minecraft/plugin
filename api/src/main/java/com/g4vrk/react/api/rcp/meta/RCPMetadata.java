package com.g4vrk.react.api.rcp.meta;

import com.g4vrk.react.api.extension.GitHubSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code rcp-metadata.yml} of an RCP.
 *
 * @param name       the RCP name, also its folder name
 * @param version    the RCP version
 * @param mainClass  the class that implements {@code RCPLifecycle}
 * @param apiVersion the {@code api-version} the RCP is built for
 * @param github     where its releases are, for updates
 */
@ApiStatus.AvailableSince("2.0")
public record RCPMetadata(
        @NotNull String name,
        @NotNull String version,
        @NotNull String mainClass,
        int apiVersion,
        @Nullable GitHubSource github
) {

}
