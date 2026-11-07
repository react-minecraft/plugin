package com.g4vrk.react.rcp.argument;

import com.g4vrk.react.api.rcp.argument.RCPArgument;
import org.jetbrains.annotations.NotNull;

public record RegisteredArgument(@NotNull String name, @NotNull RCPArgument argument) {

    public @NotNull String permission() {
        return RCPArgument.PERMISSION_PREFIX + name;
    }

}
