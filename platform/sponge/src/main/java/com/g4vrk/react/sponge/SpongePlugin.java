package com.g4vrk.react.sponge;

import com.g4vrk.react.extension.library.RuntimeLibraryLoader;
import com.google.inject.Inject;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import java.io.File;
import java.net.URL;
import java.util.Set;

/**
 * Connects Sponge lifecycle events to the isolated React runtime.
 */
@Plugin("react")
public final class SpongePlugin {

    @Inject
    private PluginContainer container;
    private Runnable[] lifecycle;

    @Listener
    public void onServerStarted(final StartedEngineEvent<Server> event) {

        if (lifecycle != null) {
            return;
        }
        final URL codeSource = getClass().getProtectionDomain().getCodeSource().getLocation();
        final File pluginDirectory = Sponge.configManager().pluginConfig(container).directory().toFile();
        final ClassLoader packetEventsLoader = compatiblePacketEvents();
        container.logger().info(packetEventsLoader == null
                ? "Using embedded PacketEvents"
                : "Using installed PacketEvents");
        final Runnable[] loaded = RuntimeLibraryLoader.start(
                codeSource,
                getClass().getClassLoader(),
                pluginDirectory.toPath(),
                container.logger()::warn,
                "com.g4vrk.react.sponge.SpongeRuntime",
                PluginContainer.class,
                container,
                packetEventsLoader,
                Set.of(
                        "org.spongepowered.",
                        "net.kyori.",
                        "org.slf4j.",
                        "io.leangen.geantyref.",
                        "com.github.retrooper.packetevents.",
                        "io.github.retrooper.packetevents."
                ),
                Set.of("com.github.retrooper.packetevents.", "io.github.retrooper.packetevents.")
        );
        this.lifecycle = loaded;
        loaded[0].run();

    }

    @Listener
    public void onServerStopping(final StoppingEngineEvent<Server> event) {

        final Runnable[] loaded = lifecycle;
        lifecycle = null;
        if (loaded != null) {
            loaded[1].run();
        }

    }

    private ClassLoader compatiblePacketEvents() {

        final PluginContainer external = Sponge.pluginManager().plugin("packetevents").orElse(null);
        if (external == null) {
            return null;
        }
        final String[] parts = external.metadata().version().toString().split("[.-]");
        try {
            if (parts.length < 2 || Integer.parseInt(parts[0]) != 2 || Integer.parseInt(parts[1]) < 13) {
                container.logger().warn("Installed PacketEvents is incompatible; using embedded version");
                return null;
            }
            final ClassLoader externalLoader = external.instance().getClass().getClassLoader();
            final Class<?> api = Class.forName("com.github.retrooper.packetevents.PacketEvents", false, externalLoader);
            if (api.getClassLoader() != externalLoader || api.getMethod("getAPI").invoke(null) == null) {
                container.logger().warn("Installed PacketEvents API is unavailable; using embedded version");
                return null;
            }
            return externalLoader;
        } catch (final NumberFormatException | ReflectiveOperationException | LinkageError failure) {
            container.logger().warn("Could not use installed PacketEvents; using embedded version: {}", failure.getMessage());
            return null;
        }

    }

}
