package com.g4vrk.react.extension;

import com.g4vrk.react.api.rcp.argument.RCPArgument;
import org.jetbrains.annotations.NotNull;

/**
 * A {@code /react <name>} subcommand added by an addon or RCP.
 *
 * @param name     the lower-case subcommand name
 * @param owner    the extension that added it
 * @param argument the handler
 */
public record RegisteredArgument(@NotNull String name, @NotNull String owner, @NotNull RCPArgument argument) {

    /**
     * The permission needed to run and see the subcommand.
     *
     * @return {@code react.command.<name>}
     */
    public @NotNull String permission() {
        return RCPArgument.PERMISSION_PREFIX + name;
    }

}
