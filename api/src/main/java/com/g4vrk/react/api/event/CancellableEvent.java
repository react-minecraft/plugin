package com.g4vrk.react.api.event;

import org.jetbrains.annotations.ApiStatus;

/**
 * An {@link Event} that implements {@link Cancellable} with a plain flag.
 */
@ApiStatus.AvailableSince("2.0")
public abstract class CancellableEvent extends Event implements Cancellable {

    private volatile boolean cancelled;

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void cancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }

}
