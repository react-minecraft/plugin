package com.g4vrk.react.api.extension;

import com.g4vrk.react.api.rcp.argument.RCPArgument;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * The {@code /react <name>} arguments one extension adds.
 *
 * <p>The plugin checks {@code react.command.<name>} before it runs an argument. Names are
 * case-insensitive, and the names of the plugin's own arguments are taken.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface ArgumentRegistry {

    /**
     * Adds {@code /react <name>}.
     *
     * @param name     the argument name
     * @param argument what runs it
     * @throws IllegalArgumentException when the plugin or another extension already has this name
     */
    void register(final @NotNull String name, final @NotNull RCPArgument argument);

    /**
     * Removes an argument this extension added.
     *
     * @param name the argument name
     * @return whether this extension had it
     */
    boolean unregister(final @NotNull String name);

    /**
     * The arguments this extension added, lower-cased.
     *
     * @return the names
     */
    @NotNull Set<String> keys();

}
