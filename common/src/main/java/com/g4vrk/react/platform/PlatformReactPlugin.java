package com.g4vrk.react.platform;

import com.g4vrk.react.extension.library.RuntimeLibraries;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Connects the Bukkit lifecycle to React's separately loaded runtime.
 */
public abstract class PlatformReactPlugin extends JavaPlugin {

    private final String runtimeClass;
    private @Nullable Runnable[] lifecycle;

    protected PlatformReactPlugin(final @NotNull String runtimeClass) {

        this.runtimeClass = runtimeClass;

    }

    @Override
    public final void onLoad() {

        lifecycle = RuntimeLibraries.start(this, runtimeClass);

    }

    @Override
    public final void onEnable() {

        final Runnable[] loaded = lifecycle;
        if (loaded == null) {
            throw new IllegalStateException("React did not finish loading");
        }
        loaded[0].run();

    }

    @Override
    public final void onDisable() {

        final Runnable[] loaded = lifecycle;
        lifecycle = null;
        if (loaded != null) {
            loaded[1].run();
        }

    }

}
