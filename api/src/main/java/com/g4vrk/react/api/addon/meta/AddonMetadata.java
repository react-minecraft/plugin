package com.g4vrk.react.api.addon.meta;

import com.g4vrk.react.api.extension.GitHubSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * The {@code react-addon.yml} of an addon.
 *
 * @param name         the addon name, also its folder name
 * @param version      the addon version
 * @param mainClass    the class that extends {@code JavaAddon}
 * @param apiVersion   the {@code api-version} the addon is built for
 * @param description  what the addon does
 * @param authors      who wrote it
 * @param contributors who helped
 * @param github       where its releases are, for updates
 */
@ApiStatus.AvailableSince("2.0")
public record AddonMetadata(
        @NotNull String name,
        @NotNull String version,
        @NotNull String mainClass,
        int apiVersion,
        @Nullable String description,
        @Nullable Collection<String> authors,
        @Nullable Collection<String> contributors,
        @Nullable GitHubSource github
) {

}
