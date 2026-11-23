package com.g4vrk.react.fabric;

import com.g4vrk.react.React;
import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.fabric.impl.task.runner.factory.FabricSchedulerFactory;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Initializes React when Fabric has started the server and stops it with the host lifecycle.
 */
public final class FabricRuntime {

    private FabricRuntime() {

    }

    public static @NotNull Runnable[] initialize(
            final @NotNull FabricLoader fabricLoader
    ) {

        final React react = React.INSTANCE;
        final AtomicBoolean running = new AtomicBoolean();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> start(server, fabricLoader, react, running));
        return new Runnable[]{
                () -> {

                },
                () -> stop(react, running)
        };

    }

    private static void start(
            final @NotNull MinecraftServer server,
            final @NotNull FabricLoader fabricLoader,
            final @NotNull React react,
            final @NotNull AtomicBoolean running
    ) {

        if (!running.compareAndSet(false, true)) {
            return;
        }
        boolean initialized = false;
        try {
            final TaskScheduler<Entity> scheduler = SchedulaAPI.<Entity>builder()
                    .factory(new FabricSchedulerFactory(server))
                    .build()
                    .createScheduler();
            final FabricPlatform platform = new FabricPlatform(fabricLoader, scheduler);
            platform.server(server);
            react.initialize(platform);
            initialized = true;
            platform.registerCommands();
            react.load();
        } catch (final RuntimeException | Error failure) {
            if (initialized) {
                try {
                    react.terminate();
                } catch (final RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            running.set(false);
            throw failure;
        }

    }

    private static void stop(
            final @NotNull React react,
            final @NotNull AtomicBoolean running
    ) {

        if (running.compareAndSet(true, false)) {
            react.terminate();
        }

    }

}
