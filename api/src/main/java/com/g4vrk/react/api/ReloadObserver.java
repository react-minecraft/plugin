package com.g4vrk.react.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Something that rereads its settings when {@code /react reload} runs.
 *
 * @param <C> the settings it reads
 */
@ApiStatus.AvailableSince("2.0")
public interface ReloadObserver<C> {

    void onReload(@NotNull C config);

}
