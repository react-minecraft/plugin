package com.g4vrk.react.paper;

import com.g4vrk.react.extension.library.RuntimeLibraries;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Starts the Paper runtime after its shared libraries are available.
 */
public final class PaperReactPlugin extends JavaPlugin {

    private Runnable[] lifecycle;

    @Override
    public void onLoad() {

        lifecycle = RuntimeLibraries.start(this, "com.g4vrk.react.paper.PaperRuntime");

    }

    @Override
    public void onEnable() {

        if (lifecycle == null) {
            throw new IllegalStateException("React did not finish loading");
        }
        lifecycle[0].run();

    }

    @Override
    public void onDisable() {

        if (lifecycle != null) {
            lifecycle[1].run();
            lifecycle = null;
        }

    }

}
