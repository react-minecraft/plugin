package com.g4vrk.react.folia;

import com.g4vrk.react.React;
import com.g4vrk.react.folia.impl.FoliaReactAPI;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes the Folia implementation within the React runtime class loader.
 */
public final class FoliaRuntime {

    public static @NotNull Runnable[] initialize(final @NotNull JavaPlugin plugin) {

        final React react = React.INSTANCE;
        react.initialize(plugin, new FoliaReactAPI(plugin));
        react.preLoad();
        return new Runnable[]{react::load, react::terminate};

    }

}
