package com.g4vrk.react.listeners.bukkit;

import com.g4vrk.react.React;
import com.g4vrk.react.api.channel.BuiltinChannels;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.factory.PlayerFactory;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.storage.StorageManager;
import com.g4vrk.react.storage.ViolationLevel;
import com.g4vrk.schedula.task.TickSchedule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Registers players and loads their persistent data for the lifetime of each session.
 */
public class ConnectionListener implements Listener {

    private final Logger logger = React.INSTANCE.logger();

    private final PlayerRegistry playerRegistry;
    private final PlayerFactory playerFactory;
    private final StorageManager storageManager;

    private final Consumer<Player> onJoin;
    private final Consumer<Player> onQuit;

    public ConnectionListener(
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull PlayerFactory playerFactory,
            final @NotNull StorageManager storageManager,
            final @NotNull Consumer<Player> onJoin,
            final @NotNull Consumer<Player> onQuit
    ) {
        this.playerRegistry = playerRegistry;
        this.playerFactory = playerFactory;
        this.storageManager = storageManager;
        this.onJoin = onJoin;
        this.onQuit = onQuit;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUserLogin(final @NotNull PlayerJoinEvent event) {
        try {
            registerPlayer(event.getPlayer());
        } catch (final Exception ex) {
            logger.error("Could not handle login of a player", ex);
        }

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUserDisconnect(final @NotNull PlayerQuitEvent event) {

        final Player bukkitPlayer = event.getPlayer();
        final UUID uniqueId = bukkitPlayer.getUniqueId();

        try {
            final TrackedPlayerImpl player = playerRegistry.get(uniqueId);
            if (player != null) {
                storageManager.detachPlayer(player);
            }
        } catch (final Exception ex) {
            logger.error("Could not store violation levels on disconnect", ex);
        }

        try {
            playerRegistry.unregister(uniqueId);
        } catch (final Exception ex) {
            logger.error("Could not unregister a disconnected player", ex);
        }

        try {
            BuiltinChannels.ALERTS.remove(bukkitPlayer);
            BuiltinChannels.MONITOR.remove(bukkitPlayer);
            BuiltinChannels.DEBUG.remove(bukkitPlayer);
            onQuit.accept(bukkitPlayer);
        } catch (final Exception ex) {
            logger.error("Could not handle disconnect of a player", ex);
        }

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        clearTransientState(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerRespawnEvent event) {
        clearTransientState(event.getPlayer());
    }

    private void clearTransientState(final Player bukkitPlayer) {
        TrackedPlayerImpl player = playerRegistry.get(bukkitPlayer.getUniqueId());
        if (player != null) {
            player.clearTransientState();
        }
    }

    public void registerPlayer(final @NotNull Player bukkitPlayer) {
        final UUID uniqueId = bukkitPlayer.getUniqueId();
        final TrackedPlayerImpl entity = playerFactory.create(uniqueId, bukkitPlayer.getName(), bukkitPlayer);

        if (entity != null) {
            final TrackedPlayerImpl previous = playerRegistry.get(uniqueId);
            if (previous != null) {
                storageManager.detachPlayer(previous);
            }
            playerRegistry.register(uniqueId, entity);
            final Hydration hydration = new Hydration(entity);
            hydration.load();
            React.INSTANCE.scheduler().scheduleEntity(bukkitPlayer, task -> {
                if (playerRegistry.get(uniqueId) != entity) {
                    task.cancel();
                    return;
                }
                entity.refreshRotationPermission();
                hydration.retryIfAvailable();
            }, TickSchedule.repeating(1, 1));

            onJoin.accept(bukkitPlayer);
        }
    }

    /**
     * Retries failed reads only while the player's entity task is active.
     */
    private final class Hydration {

        private final TrackedPlayerImpl entity;
        private final List<ViolationLevel> levels;

        private volatile boolean retry;
        private long nextAttempt;

        private Hydration(final @NotNull TrackedPlayerImpl entity) {

            this.entity = entity;
            this.levels = entity.checks().stream().map(Check::violationLevel).toList();

        }

        private void retryIfAvailable() {

            if (retry && storageManager.available() && System.nanoTime() - nextAttempt >= 0) {
                load();
            }

        }

        private void load() {

            retry = false;
            nextAttempt = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
            storageManager.loadPlayer(entity.uniqueId(), levels).whenComplete((data, failure) -> {
                if (playerRegistry.get(entity.uniqueId()) != entity) {
                    return;
                }
                try {
                    if (failure != null) {
                        if (!entity.dataReady()) {
                            logger.debug("Could not load stored data for {}; checks will continue in fail-open mode", entity.name(), failure);
                        }
                        retry = true;
                    } else {
                        entity.applyStorageData(data);
                    }
                } finally {
                    entity.markDataReady();
                }
            });

        }

    }

}
