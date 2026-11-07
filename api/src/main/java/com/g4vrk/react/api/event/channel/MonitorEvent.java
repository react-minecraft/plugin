package com.g4vrk.react.api.event.channel;

import com.g4vrk.react.api.channel.CheckReport;
import com.g4vrk.react.api.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Posted before a monitor line goes to its recipients. Cancel it to send nothing.
 */
@ApiStatus.AvailableSince("2.0")
public final class MonitorEvent extends CancellableEvent {

    private final CheckReport report;

    /**
     * Creates the event.
     *
     * @param report the monitor line
     */
    public MonitorEvent(
            final @NotNull CheckReport report
    ) {
        this.report = report;
    }

    /**
     * Gives the monitor line.
     *
     * @return the monitor line
     */
    public @NotNull CheckReport report() {
        return report;
    }

}
