package com.g4vrk.react.bukkit;

import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.bukkit.impl.task.runner.factory.PaperSchedulerFactory;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes the Bukkit implementation within the React runtime class loader.
 */
public final class SpigotRuntime {

    private SpigotRuntime() {

    }

    public static @NotNull Runnable[] initialize(final @NotNull JavaPlugin plugin) {

        return BukkitRuntime.initialize(
                plugin,
                SchedulaAPI.<Entity>builder().factory(new PaperSchedulerFactory(plugin)).build().createScheduler(),
                "bukkit"
        );

    }

}
