package com.g4vrk.react.api.event;

import org.jetbrains.annotations.ApiStatus;

/**
 * A registered handler, kept to remove it later.
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface Subscription {

    /**
     * Removes the handler. Calling it again does nothing.
     */
    void unsubscribe();

}
