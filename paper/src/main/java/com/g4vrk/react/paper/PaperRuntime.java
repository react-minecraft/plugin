package com.g4vrk.react.paper;

import com.g4vrk.react.React;
import com.g4vrk.react.paper.impl.PaperReactAPI;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Initializes the Paper implementation within the React runtime class loader.
 */
public final class PaperRuntime {

    public static Runnable[] initialize(final JavaPlugin plugin) {

        final React react = React.INSTANCE;
        react.initialize(plugin, new PaperReactAPI(plugin));
        react.preLoad();
        return new Runnable[]{react::load, react::terminate};

    }

}
