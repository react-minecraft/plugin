package com.g4vrk.react.api;

import org.jetbrains.annotations.NotNull;

/**
 * Something that rereads its settings when {@code /react reload} runs.
 *
 * @param <C> the settings it reads
 */
public interface ReloadObserver<C> {

    void onReload(@NotNull C config);

}
