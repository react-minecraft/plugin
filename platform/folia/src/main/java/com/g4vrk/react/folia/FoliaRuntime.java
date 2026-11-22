package com.g4vrk.react.folia;

import com.g4vrk.react.bukkit.BukkitRuntime;
import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.folia.impl.task.runner.factory.FoliaSchedulerFactory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes the Folia implementation within the React runtime class loader.
 */
public final class FoliaRuntime {

    public static @NotNull Runnable[] initialize(final @NotNull JavaPlugin plugin) {

        return BukkitRuntime.initialize(
                plugin,
                SchedulaAPI.builder().factory(new FoliaSchedulerFactory(plugin)).build().createScheduler(),
                "folia"
        );

    }

}
