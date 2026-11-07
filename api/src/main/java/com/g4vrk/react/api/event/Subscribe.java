package com.g4vrk.react.api.event;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a listener object as an event handler.
 *
 * <p>The method takes exactly one parameter, the event type it handles, and also receives
 * subtypes of that event.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ApiStatus.AvailableSince("2.0")
public @interface Subscribe {

    /**
     * Gives the priority of the handler.
     *
     * @return when the handler runs relative to others
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * Tells whether the handler skips events that are already cancelled.
     *
     * @return {@code true} to skip cancelled events
     */
    boolean ignoreCancelled() default false;

}
