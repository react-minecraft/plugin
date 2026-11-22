package com.g4vrk.react.platform.reboot;

import org.jetbrains.annotations.NotNull;

/**
 * Preformatted legacy status strings sent during a live plugin reboot after class unloading.
 *
 * @param unloaded message sent after unloading the running plugin
 * @param loaded   message sent after loading the replacement plugin
 * @param failed   message sent when unloading or loading fails
 */
public record PluginRebootMessage(
        @NotNull String unloaded,
        @NotNull String loaded,
        @NotNull String failed
) {

}
