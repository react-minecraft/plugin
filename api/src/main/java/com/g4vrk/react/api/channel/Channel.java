package com.g4vrk.react.api.channel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.AvailableSince("2.0")
public interface Channel<I> {

    void publish(final @NotNull I input);

}
