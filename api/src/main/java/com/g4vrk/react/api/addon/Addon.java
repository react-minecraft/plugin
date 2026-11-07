package com.g4vrk.react.api.addon;

import com.g4vrk.react.api.addon.meta.AddonMetadata;
import com.g4vrk.react.api.extension.AddonEnvironment;
import com.g4vrk.react.resource.ResourceHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * An addon: a jar in {@code plugins/React/addons} with a {@code react-addon.yml}.
 *
 * <p>Addons extend {@link JavaAddon}. The plugin calls {@link #onLoad()} once the addon is
 * created, then {@link #onEnable()}, and {@link #onDisable()} when the plugin stops or the
 * addon is rebooted with {@code /react reboot <name>}.</p>
 */
@ApiStatus.AvailableSince("2.0")
public interface Addon extends ResourceHolder {

    /**
     * What the plugin gives this addon.
     *
     * @return the environment
     */
    @NotNull AddonEnvironment environment();

    /**
     * The addon's folder, {@code addons/<name>}.
     *
     * @return the data folder
     */
    @NotNull Path directory();

    /**
     * The name from {@code react-addon.yml}.
     *
     * @return the addon name
     */
    @NotNull String name();

    /**
     * The addon's {@code react-addon.yml}.
     *
     * @return the metadata
     */
    @NotNull AddonMetadata metadata();

    /**
     * A logger named after the addon.
     *
     * @return the logger
     */
    @NotNull Logger logger();

    /**
     * Whether {@link #onEnable()} ran and {@link #onDisable()} has not.
     *
     * @return whether the addon is enabled
     */
    boolean enabled();

    /**
     * Called once the addon is created, before it is enabled.
     */
    default void onLoad() {
    }

    /**
     * Called when the addon starts.
     */
    void onEnable();

    /**
     * Called when the addon stops. Everything registered through {@link #environment()}
     * is removed after this returns.
     */
    void onDisable();

}
