package com.g4vrk.react.service;

import com.g4vrk.react.PermissionKey;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.channel.registry.BuiltinChannelRegistry;
import com.g4vrk.react.api.channel.bind.PermissionChannelBinder;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.check.processor.rotation.RotationProcessor;
import com.g4vrk.react.config.ConfigManager;
import com.g4vrk.react.config.model.MainConfig;
import com.g4vrk.react.platform.listener.CoreListenerCallback;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.factory.PlayerFactory;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.storage.StorageManager;
import com.g4vrk.react.storage.ViolationLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Pure-Java gameplay and session listener coordinator.
 *
 * <p>Receives platform-normalized callbacks from {@link PlatformListenerBridge} and manages player
 * session lifecycles, persistent data hydration, combat windows, rotation processing, channel
 * auto-subscriptions, and optional platform placeholder bridges.</p>
 */
public final class ListenerService implements CoreListenerCallback, ReloadObserver<MainConfig> {

    private final ReactPlatform platform;
    private final PlatformListenerBridge platformListeners;
    private final Logger logger;
    private final RotationProcessor rotationProcessor = new RotationProcessor();
    private final Map<UUID, Task> entityTasks = new ConcurrentHashMap<>();

    private PlayerRegistry playerRegistry;
    private PlayerFactory playerFactory;
    private StorageManager storageManager;
    private RCPHost rcpHost;
    private Consumer<PlatformPlayer> onJoin;
    private PermissionChannelBinder alertsBinder;
    private PermissionChannelBinder monitorBinder;

    private volatile boolean closed;
    private volatile long combatTicks = 1L;

    public ListenerService(
            final @NotNull ReactPlatform platform,
            final @NotNull PlatformListenerBridge platformListeners,
            final @NotNull Logger logger
    ) {

        this.platform = platform;
        this.platformListeners = platformListeners;
        this.logger = logger;

    }

    public void register(
            final @NotNull ConfigManager configs,
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull PlayerFactory playerFactory,
            final @NotNull StorageManager storage,
            final @NotNull RCPHost host,
            final @NotNull Consumer<PlatformPlayer> onJoin
    ) {

        this.closed = false;
        this.playerRegistry = playerRegistry;
        this.playerFactory = playerFactory;
        this.storageManager = storage;
        this.rcpHost = host;
        this.onJoin = onJoin;
        this.alertsBinder = new PermissionChannelBinder(PermissionKey.ALERTS_ENABLE_ON_JOIN, BuiltinChannelRegistry.ALERTS, false);
        this.monitorBinder = new PermissionChannelBinder(PermissionKey.MONITOR_ENABLE_ON_JOIN, BuiltinChannelRegistry.MONITOR, false);
        this.onReload(configs.main());

        for (final PlatformPlayer online : platform.onlinePlayers()) {
            onJoin(online);
        }
        platformListeners.registerCore(this);

        platform.registerPlaceholders(
                playerRegistry,
                React.INSTANCE.placeholders(),
                () -> {
                    final String status = host.status();
                    return status == null ? configs.actions().texts().unavailable() : status;
                },
                logger
        );

    }

    public void activateCombat(
            final @Nullable TrackedPlayerImpl player
    ) {

        if (player != null) {
            player.extendCombat(combatTicks);
        }

    }

    public void reload() {

        if (React.INSTANCE.configs() != null) {
            this.onReload(React.INSTANCE.configs().main());
        }

    }

    @Override
    public void onReload(
            final @NotNull MainConfig config
    ) {

        this.combatTicks = Math.max(1L, config.player().combat().time().toMillis() / 50L);

    }

    @Override
    public void onJoin(
            final @NotNull PlatformPlayer platformPlayer
    ) {

        if (closed) {
            return;
        }
        try {
            final UUID uniqueId = platformPlayer.uniqueId();
            final TrackedPlayerImpl entity = playerFactory.create(platformPlayer);
            if (entity == null) {
                return;
            }
            final TrackedPlayerImpl previous = playerRegistry.get(uniqueId);
            if (previous != null) {
                storageManager.detachPlayer(previous);
            }
            final Task previousTask = entityTasks.remove(uniqueId);
            if (previousTask != null) {
                previousTask.cancel();
            }
            playerRegistry.register(uniqueId, entity);
            final Hydration hydration = new Hydration(entity);
            hydration.load();
            final Task scheduled = React.INSTANCE.scheduler().scheduleEntity(platformPlayer, task -> {
                if (closed || playerRegistry.get(uniqueId) != entity) {
                    task.cancel();
                    entityTasks.remove(uniqueId, task);
                    return;
                }
                entity.refreshBypassPermissions();
                hydration.retryIfAvailable();
            }, TickSchedule.repeating(1, 20));
            entityTasks.put(uniqueId, scheduled);
            if (closed && entityTasks.remove(uniqueId, scheduled)) {
                scheduled.cancel();
                return;
            }

            alertsBinder.accept(platformPlayer);
            monitorBinder.accept(platformPlayer);
            onJoin.accept(platformPlayer);
        } catch (final Exception ex) {
            logger.error("Could not handle login of a player", ex);
        }

    }

    @Override
    public void onQuit(
            final @NotNull PlatformPlayer platformPlayer
    ) {

        final UUID uniqueId = platformPlayer.uniqueId();
        final Task task = entityTasks.remove(uniqueId);
        if (task != null) {
            task.cancel();
        }

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
            BuiltinChannelRegistry.ALERTS.remove(platformPlayer);
            BuiltinChannelRegistry.MONITOR.remove(platformPlayer);
            BuiltinChannelRegistry.DEBUG.remove(platformPlayer);
            rcpHost.quit(uniqueId);
        } catch (final Exception ex) {
            logger.error("Could not handle disconnect of a player", ex);
        }

    }

    @Override
    public void onTransientReset(
            final @NotNull UUID playerId
    ) {

        final TrackedPlayerImpl player = playerRegistry.get(playerId);
        if (player != null) {
            player.clearTransientState();
        }

    }

    @Override
    public void onCombatHit(
            final @NotNull UUID attackerId,
            final int targetEntityId
    ) {

        final TrackedPlayerImpl attacker = playerRegistry.get(attackerId);
        activateCombat(attacker);
        if (attacker != null) {
            rcpHost.attack(attacker, targetEntityId);
        }

    }

    @Override
    public void onRotation(
            final @NotNull UUID playerId,
            final float yaw,
            final float pitch
    ) {

        final TrackedPlayerImpl player = playerRegistry.get(playerId);
        if (player != null) {
            rotationProcessor.process(player, yaw, pitch);
        }

    }

    public void close() {

        this.closed = true;
        for (final Task task : entityTasks.values()) {
            try {
                task.cancel();
            } catch (final Exception ex) {
                logger.warn("Could not cancel player hydration task", ex);
            }
        }
        entityTasks.clear();

        try {
            rotationProcessor.close();
        } catch (final Exception ex) {
            logger.warn("Could not clear rotation processor state", ex);
        }

        try {
            platformListeners.unregisterCore();
        } catch (final Exception ex) {
            logger.error("Could not unregister platform listeners", ex);
        }

        try {
            platform.unregisterPlaceholders(logger);
        } catch (final Exception ex) {
            logger.error("Could not unregister platform placeholders", ex);
        }

        try {
            BuiltinChannelRegistry.ALERTS.clear();
            BuiltinChannelRegistry.MONITOR.clear();
            BuiltinChannelRegistry.DEBUG.clear();
        } catch (final Exception ex) {
            logger.error("Could not clear channel recipients", ex);
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

        private Hydration(
                final @NotNull TrackedPlayerImpl entity
        ) {

            this.entity = entity;
            final List<Check> checks = entity.checks();
            final List<ViolationLevel> collected = new ArrayList<>(checks.size());
            for (final Check check : checks) {
                collected.add(check.violationLevel());
            }
            this.levels = List.copyOf(collected);

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
