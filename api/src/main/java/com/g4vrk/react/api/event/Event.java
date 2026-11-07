package com.g4vrk.react.api.event;

import org.jetbrains.annotations.ApiStatus;

/**
 * Base of every event posted on the {@link EventBus}.
 *
 * <p>Events are posted on the thread that caused them, which on Folia may be any region thread.
 * Handlers must not assume the main thread.</p>
 */
@ApiStatus.AvailableSince("2.0")
public abstract class Event {

}
