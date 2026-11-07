package com.g4vrk.react.api.event;

import org.jetbrains.annotations.ApiStatus;

/**
 * An event whose outcome a handler can stop.
 */
@ApiStatus.AvailableSince("2.0")
public interface Cancellable {

    /**
     * Tells whether a handler cancelled the event.
     *
     * @return {@code true} when the plugin will skip the action
     */
    boolean cancelled();

    /**
     * Cancels the event or takes the cancel back.
     *
     * @param cancelled {@code true} to skip the action
     */
    void cancelled(final boolean cancelled);

}
