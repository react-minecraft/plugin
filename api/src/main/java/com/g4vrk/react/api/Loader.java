package com.g4vrk.react.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.AvailableSince("2.0")
@FunctionalInterface
public interface Loader<R, S> {

    @NotNull R load(final @NotNull S source) throws Exception;

}
