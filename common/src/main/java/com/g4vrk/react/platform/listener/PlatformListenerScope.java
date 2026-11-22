package com.g4vrk.react.platform.listener;

import org.jetbrains.annotations.NotNull;

/**
 * Tracks platform-native listeners owned by one extension and removes them on {@link #close()}.
 */
public interface PlatformListenerScope extends AutoCloseable {

    /**
     * Registers a listener if it matches any listener type supported by this platform.
     *
     * @param listener the candidate listener instance
     */
    void register(
            final @NotNull Object listener
    );

    /**
     * Unregisters a previously registered platform listener.
     *
     * @param listener the listener instance
     */
    void unregister(
            final @NotNull Object listener
    );

    @Override
    void close();

}
