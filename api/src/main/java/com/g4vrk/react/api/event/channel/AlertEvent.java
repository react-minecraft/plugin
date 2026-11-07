package com.g4vrk.react.api.event.channel;

import com.g4vrk.react.api.channel.CheckReport;
import com.g4vrk.react.api.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Posted before an alert goes to its recipients. Cancel it to send nothing.
 */
@ApiStatus.AvailableSince("2.0")
public final class AlertEvent extends CancellableEvent {

    private final CheckReport report;

    /**
     * Creates the event.
     *
     * @param report the alert
     */
    public AlertEvent(
            final @NotNull CheckReport report
    ) {
        this.report = report;
    }

    /**
     * Gives the alert.
     *
     * @return the alert
     */
    public @NotNull CheckReport report() {
        return report;
    }

}
