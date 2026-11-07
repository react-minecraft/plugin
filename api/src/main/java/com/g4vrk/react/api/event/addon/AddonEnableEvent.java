package com.g4vrk.react.api.event.addon;

import com.g4vrk.react.api.event.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Posted after an addon is enabled.
 */
@ApiStatus.AvailableSince("2.0")
public final class AddonEnableEvent extends Event {

    private final String name;
    private final String version;

    /**
     * Creates the event.
     *
     * @param name    the addon name
     * @param version the addon version
     */
    public AddonEnableEvent(
            final @NotNull String name,
            final @NotNull String version
    ) {
        this.name = name;
        this.version = version;
    }

    /**
     * Gives the addon name.
     *
     * @return the addon name
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * Gives the addon version.
     *
     * @return the addon version
     */
    public @NotNull String version() {
        return version;
    }

}
