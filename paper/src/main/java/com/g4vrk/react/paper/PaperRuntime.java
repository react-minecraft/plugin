package com.g4vrk.react.paper;

import com.g4vrk.react.React;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import com.g4vrk.react.bukkit.BukkitPlatform;
import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.bukkit.impl.task.runner.factory.PaperSchedulerFactory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes the Paper implementation within the React runtime class loader.
 */
public final class PaperRuntime {

    public static @NotNull Runnable[] initialize(final @NotNull JavaPlugin plugin) {

        PacketEventsAPI<?> packetEvents = PacketEvents.getAPI();
        final boolean embedded = packetEvents == null;
        if (embedded) {
            packetEvents = SpigotPacketEventsBuilder.build(plugin);
            packetEvents.getSettings().checkForUpdates(false);
            PacketEvents.setAPI(packetEvents);
            packetEvents.load();
        }
        final PacketEventsAPI<?> selected = packetEvents;
        final React react = React.INSTANCE;
        react.initialize(new BukkitPlatform(plugin, SchedulaAPI.builder().factory(new PaperSchedulerFactory(plugin)).build().createScheduler(), "paper", "paper"));
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

    }

}
