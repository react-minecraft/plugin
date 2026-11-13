package com.g4vrk.react.folia;

import com.g4vrk.react.React;
import com.g4vrk.react.folia.impl.FoliaReactAPI;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Initializes the Folia implementation within the React runtime class loader.
 */
public final class FoliaRuntime {

    public static Runnable[] initialize(final JavaPlugin plugin) {

        final React react = React.INSTANCE;
        react.initialize(plugin, new FoliaReactAPI(plugin));
        react.preLoad();
        return new Runnable[]{react::load, react::terminate};

    }

}
