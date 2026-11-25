package com.g4vrk.react.api.channel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A destination that receives typed notifications or reports.
 *
 * @param <I> the payload type published to this channel
 */
@ApiStatus.AvailableSince("2.0")
public interface Channel<I> {

    void publish(final @NotNull I input);

}
