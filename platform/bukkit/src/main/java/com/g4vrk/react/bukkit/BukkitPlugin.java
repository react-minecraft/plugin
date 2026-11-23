package com.g4vrk.react.bukkit;

import com.g4vrk.react.extension.library.RuntimeLibraryLoader;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Connects the Bukkit plugin lifecycle to React's isolated runtime class loader.
 */
public abstract class BukkitPlugin extends JavaPlugin {

    private final String runtimeClass;
    private @Nullable Runnable[] lifecycle;

    protected BukkitPlugin(
            final @NotNull String runtimeClass
    ) {

        this.runtimeClass = runtimeClass;

    }

    @Override
    public final void onLoad() {

        final ClassLoader packetEventsLoader = compatiblePacketEvents();
        final boolean embedded = packetEventsLoader == null;
        getLogger().info(embedded ? "Using embedded PacketEvents" : "Using installed PacketEvents");
        this.lifecycle = RuntimeLibraryLoader.start(
                getClass().getProtectionDomain().getCodeSource().getLocation(),
                getClass().getClassLoader(),
                getDataFolder().toPath(),
                message -> getLogger().warning(message),
                runtimeClass,
                JavaPlugin.class,
                this,
                packetEventsLoader,
                Set.of("com.github.retrooper.packetevents.", "io.github.retrooper.packetevents.", "net.kyori."),
                Set.of("com.github.retrooper.packetevents.", "io.github.retrooper.packetevents.")
        );

    }

    private @Nullable ClassLoader compatiblePacketEvents() {

        final Plugin external = getServer().getPluginManager().getPlugin("packetevents");
        if (external == null) {
            return null;
        }
        final String[] parts = external.getDescription().getVersion().split("[.-]");
        try {
            if (parts.length < 2 || Integer.parseInt(parts[0]) != 2 || Integer.parseInt(parts[1]) < 13) {
                getLogger().warning("Installed PacketEvents is incompatible; using embedded version");
                return null;
            }
            final Class<?> api = Class.forName("com.github.retrooper.packetevents.PacketEvents", false, external.getClass().getClassLoader());
            if (api.getClassLoader() != external.getClass().getClassLoader() || api.getMethod("getAPI").invoke(null) == null) {
                getLogger().warning("Installed PacketEvents API is unavailable; using embedded version");
                return null;
            }
            return external.getClass().getClassLoader();
        } catch (final NumberFormatException | ReflectiveOperationException | LinkageError failure) {
            getLogger().warning("Could not use installed PacketEvents; using embedded version: " + failure.getMessage());
            return null;
        }

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
