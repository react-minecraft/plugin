package com.g4vrk.react.paper;

import com.g4vrk.react.bukkit.BukkitRuntime;
import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.bukkit.impl.task.runner.factory.PaperSchedulerFactory;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes the Paper implementation within the React runtime class loader.
 */
public final class PaperRuntime {

    public static @NotNull Runnable[] initialize(final @NotNull JavaPlugin plugin) {

        return BukkitRuntime.initialize(
                plugin,
                SchedulaAPI.<Entity>builder().factory(new PaperSchedulerFactory(plugin)).build().createScheduler(),
                "paper"
        );

    }

}
