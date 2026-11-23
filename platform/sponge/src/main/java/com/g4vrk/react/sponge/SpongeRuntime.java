package com.g4vrk.react.sponge;

import com.g4vrk.react.React;
import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.sponge.impl.task.runner.factory.SpongeSchedulerFactory;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.sponge.factory.SpongePacketEventsBuilder;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.plugin.PluginContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes React and PacketEvents after the Sponge server engine has started.
 */
public final class SpongeRuntime {

    private SpongeRuntime() {

    }

    public static @NotNull Runnable[] initialize(final @NotNull PluginContainer container) {

        final PacketEventsAPI<?> installed = PacketEvents.getAPI();
        final boolean embedded = installed == null;
        final PacketEventsAPI<?> selected = embedded
                ? SpongePacketEventsBuilder.build(container)
                : installed;
        try {
            if (embedded) {
                selected.getSettings().checkForUpdates(false);
                PacketEvents.setAPI(selected);
                selected.load();
            }
            final TaskScheduler<ServerPlayer> scheduler = SchedulaAPI.<ServerPlayer>builder()
                    .factory(new SpongeSchedulerFactory(container))
                    .build()
                    .createScheduler();
            final React react = React.INSTANCE;
            react.initialize(new SpongePlatform(container, scheduler));
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
