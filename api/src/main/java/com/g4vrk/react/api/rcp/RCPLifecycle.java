package com.g4vrk.react.api.rcp;

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
