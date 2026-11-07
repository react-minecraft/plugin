package com.g4vrk.react.command.argument;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@RequiredArgsConstructor
public abstract class LocalArgument {

    public abstract @NotNull Command.Builder<CommandSender> build();

    /**
     * Gives every command this argument registers. Most register only {@link #build()}.
     *
     * @return the builders
     */
    public @NotNull List<Command.Builder<CommandSender>> builds() {
        return List.of(build());
    }

}
