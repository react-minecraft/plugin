package com.g4vrk.react.command.argument;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import lombok.RequiredArgsConstructor;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@RequiredArgsConstructor
public abstract class LocalArgument {

    public abstract @NotNull Command.Builder<PlatformSender> build();

    /**
     * Gives every command this argument registers. Most register only {@link #build()}.
     *
     * @return the builders
     */
    public @NotNull List<Command.Builder<PlatformSender>> builds() {

        return List.of(build());

    }

}
