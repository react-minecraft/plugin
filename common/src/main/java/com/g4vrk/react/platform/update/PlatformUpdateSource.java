package com.g4vrk.react.platform.update;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Platform-provided coordinates for self-updating the React plugin artifact.
 *
 * @param pluginJar       path to the running plugin JAR, or {@code null} when unavailable
 * @param updateDirectory directory where staged plugin updates are placed
 * @param artifactPrefix  release asset prefix (for example {@code react-bukkit} or {@code react-folia})
 * @param currentVersion  the running version string
 */
public record PlatformUpdateSource(
        @Nullable Path pluginJar,
        @NotNull Path updateDirectory,
        @NotNull String artifactPrefix,
        @NotNull String currentVersion
) {

}
