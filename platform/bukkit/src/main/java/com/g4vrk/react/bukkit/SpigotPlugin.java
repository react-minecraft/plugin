package com.g4vrk.react.bukkit;

import org.jetbrains.annotations.ApiStatus;

/**
 * Bukkit/Spigot plugin entry point for the Bukkit distribution.
 */
@ApiStatus.Internal
public final class SpigotPlugin extends BukkitPlugin {

    public SpigotPlugin() {

        super("com.g4vrk.react.bukkit.SpigotRuntime");

    }

}
