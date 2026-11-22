package com.g4vrk.react.bukkit;

import com.g4vrk.react.React;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Starts the shared Bukkit runtime and owns an embedded PacketEvents instance when needed.
 */
public final class BukkitRuntime {

    private BukkitRuntime() {

    }

    public static @NotNull Runnable[] initialize(
            final @NotNull JavaPlugin plugin,
            final @NotNull Scheduler scheduler,
            final @NotNull String platform
    ) {

        PacketEventsAPI<?> packetEvents = PacketEvents.getAPI();
        final boolean embedded = packetEvents == null;
        if (embedded) {
            packetEvents = SpigotPacketEventsBuilder.build(plugin);
        }
        final PacketEventsAPI<?> selected = packetEvents;
        try {
            if (embedded) {
                selected.getSettings().checkForUpdates(false);
                PacketEvents.setAPI(selected);
                selected.load();
            }
            final React react = React.INSTANCE;
            react.initialize(new BukkitPlatform(plugin, scheduler, platform, platform));
            return new Runnable[]{() -> {
                if (embedded) {
                    selected.init();
                }
                react.load();
            }, () -> {
                try {
                    react.terminate();
                } finally {
                    if (embedded) {
                        selected.terminate();
                    }
                }
            }};
        } catch (final RuntimeException | Error failure) {
            if (embedded) {
                try {
                    selected.terminate();
                } catch (final RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }

    }

}
