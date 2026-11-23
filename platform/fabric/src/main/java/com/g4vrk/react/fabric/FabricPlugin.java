package com.g4vrk.react.fabric;

import com.g4vrk.react.extension.library.RuntimeLibraryLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URL;
import java.util.Set;

/**
 * Connects Fabric mod initialization to React's isolated runtime.
 */
public final class FabricPlugin implements ModInitializer {

    private Runnable[] lifecycle;

    @Override
    public void onInitialize() {

        final FabricLoader fabricLoader = FabricLoader.getInstance();
        final URL codeSource = getClass().getProtectionDomain().getCodeSource().getLocation();
        final File dataDirectory = fabricLoader.getConfigDir().resolve("react").toFile();
        final ClassLoader packetEventsLoader = compatiblePacketEvents(fabricLoader);
        System.getLogger("React").log(
                System.Logger.Level.INFO,
                "Using PacketEvents " + fabricLoader.getModContainer("packetevents")
                        .orElseThrow()
                        .getMetadata()
                        .getVersion()
                        .getFriendlyString()
        );
        this.lifecycle = RuntimeLibraryLoader.start(
                codeSource,
                getClass().getClassLoader(),
                dataDirectory.toPath(),
                message -> System.getLogger("React").log(System.Logger.Level.WARNING, message),
                "com.g4vrk.react.fabric.FabricRuntime",
                FabricLoader.class,
                fabricLoader,
                packetEventsLoader,
                Set.of("com.github.retrooper.packetevents.", "io.github.retrooper.packetevents."),
                Set.of("com.github.retrooper.packetevents.", "io.github.retrooper.packetevents.")
        );
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());

    }

    private @NotNull ClassLoader compatiblePacketEvents(
            final @NotNull FabricLoader fabricLoader
    ) {

        try {
            final ModContainer packetEvents = fabricLoader.getModContainer("packetevents")
                    .orElseThrow(() -> new IllegalStateException("PacketEvents is missing from the Fabric mod list"));
            if (!isCompatibleVersion(packetEvents.getMetadata().getVersion().getFriendlyString())) {
                throw new IllegalStateException("PacketEvents 2.14.0 or newer in the 2.x series is required");
            }
            final Class<?> api = Class.forName(
                    "com.github.retrooper.packetevents.PacketEvents",
                    false,
                    getClass().getClassLoader()
            );
            if (api.getMethod("getAPI").invoke(null) == null) {
                throw new IllegalStateException("PacketEvents API is unavailable");
            }
            return api.getClassLoader();
        } catch (final ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("The required PacketEvents Fabric mod could not be loaded", failure);
        }

    }

    private boolean isCompatibleVersion(
            final @NotNull String version
    ) {

        final String[] parts = version.split("[.+-]", 4);
        try {
            return parts.length >= 2 && Integer.parseInt(parts[0]) == 2 && Integer.parseInt(parts[1]) >= 14;
        } catch (final NumberFormatException failure) {
            return false;
        }

    }

    private void stop() {

        final Runnable[] current = lifecycle;
        this.lifecycle = null;
        if (current != null) {
            current[1].run();
        }

    }

}
