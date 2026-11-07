package com.g4vrk.react.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.AvailableSince("2.0")
@FunctionalInterface
public interface Factory<T> {

    @NotNull T create();

}
