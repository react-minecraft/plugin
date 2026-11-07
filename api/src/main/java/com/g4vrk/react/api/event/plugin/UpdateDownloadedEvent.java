package com.g4vrk.react.api.event.plugin;

import com.g4vrk.react.api.event.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Posted after an update was downloaded and its signature checked.
 */
@ApiStatus.AvailableSince("2.0")
public final class UpdateDownloadedEvent extends Event {

    /**
     * What an update belongs to.
     */
    public enum Kind {

        PLUGIN,
        RCP,
        ADDON

    }

    private final Kind kind;
    private final String name;
    private final String version;

    /**
     * Creates the event.
     *
     * @param kind    what was updated
     * @param name    the plugin, RCP or addon name
     * @param version the downloaded version
     */
    public UpdateDownloadedEvent(
            final @NotNull Kind kind,
            final @NotNull String name,
            final @NotNull String version
    ) {
        this.kind = kind;
        this.name = name;
        this.version = version;
    }

    /**
     * Gives what was updated.
     *
     * @return what was updated
     */
    public @NotNull Kind kind() {
        return kind;
    }

    /**
     * Gives the name.
     *
     * @return the name
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * Gives the downloaded version.
     *
     * @return the downloaded version
     */
    public @NotNull String version() {
        return version;
    }

}
