package com.g4vrk.react.api.internal.runtime;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

@ApiStatus.Internal
public interface LifecycleRuntime<T> extends AutoCloseable {

    void register(
            final @NotNull T component
    );

    @NotNull CompletionStage<Void> start();

    boolean running();

    @Override
    void close();

}
