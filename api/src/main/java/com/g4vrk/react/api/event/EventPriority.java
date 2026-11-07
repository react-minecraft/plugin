package com.g4vrk.react.api.event;

import org.jetbrains.annotations.ApiStatus;

/**
 * Order in which handlers run, from first to last.
 *
 * <p>{@link #MONITOR} handlers only watch the final state and must not change the event.</p>
 */
@ApiStatus.AvailableSince("2.0")
public enum EventPriority {

    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    MONITOR

}
