package com.g4vrk.react.api.extension;

import com.g4vrk.react.api.config.ConfigFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Builds {@link ConfigFile}s for one extension.
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface ConfigFactory {

    /**
     * Starts a file in the extension folder.
     *
     * <p>The builder already has the path {@code <directory>/<name>}, the default resource
     * {@code <name>} from the extension jar and the extension logger. Migrations or
     * {@code keepMissing()} may still be added before {@code build()}.</p>
     *
     * @param name the file name, which may contain folders, such as {@code messages/en.yml}
     * @param type the mapped type
     * @param <T>  the mapped type
     * @return a builder for the file
     */
    <T> ConfigFile.@NotNull Builder<T> file(final @NotNull String name, final @NotNull Class<T> type);

}
