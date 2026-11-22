package com.g4vrk.react.platform.reboot;

import org.jetbrains.annotations.NotNull;

/**
 * A validated, platform-specific live plugin reboot ready to be scheduled after command dispatch.
 */
public interface PreparedPluginReboot {

    /**
     * Returns the replacement artifact file name.
     *
     * @return the file name
     */
    @NotNull String artifactName();

    /**
     * Returns the version declared by the replacement artifact.
     *
     * @return the new version string
     */
    @NotNull String newVersion();

    /**
     * Schedules the reboot outside the active command dispatch.
     */
    void schedule();

}
