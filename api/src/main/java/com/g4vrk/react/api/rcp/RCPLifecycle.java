package com.g4vrk.react.api.rcp;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.0")
public interface RCPLifecycle {

    void onEnable() throws Exception;

    void onDisable() throws Exception;

    /**
     * Called after {@code /react reload} has reloaded the plugin's own configuration.
     * Providers reread their files here.
     */
    default void onReload() throws Exception {
    }

}
