package com.g4vrk.react.service;

import com.g4vrk.react.Permissions;
import com.g4vrk.react.api.channel.ReactChannels;
import com.g4vrk.react.api.channel.bind.PermissionChannelBinder;
import com.g4vrk.react.check.processor.rotation.RotationFactory;
import com.g4vrk.react.check.processor.rotation.RotationProcessor;
import com.g4vrk.react.config.ReactConfigs;
import com.g4vrk.react.listeners.bukkit.CombatListener;
import com.g4vrk.react.listeners.bukkit.ConnectionListener;
import com.g4vrk.react.listeners.packet.RotationListener;
import com.g4vrk.react.placeholder.ReactPlaceholderExpansion;
import com.g4vrk.react.player.factory.PlayerFactory;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.storage.StorageManager;
import com.g4vrk.react.util.placeholder.PlaceholderAPIUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Everything React listens to: rotation packets, combat, joins and quits, the alert and verbose
 * channels players join automatically, and the PlaceholderAPI expansion.
 */
public final class ListenerService {

    private final Plugin plugin;
    private final Logger logger;

    private PacketListenerCommon[] packetListeners = new PacketListenerCommon[0];
    private CombatListener combat;
    private ReactPlaceholderExpansion placeholders;

    public ListenerService(final @NotNull Plugin plugin, final @NotNull Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void register(
            final @NotNull ReactConfigs configs,
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull PlayerFactory playerFactory,
            final @NotNull StorageManager storage,
            final @NotNull RCPHost host
    ) {
        logger.info("Registering packet listeners...");
        this.packetListeners = new PacketListenerCommon[]{
                new RotationListener(playerRegistry, new RotationProcessor(new RotationFactory()))
        };
        PacketEvents.getAPI().getEventManager().registerListeners(packetListeners);

        logger.info("Registering bukkit listeners...");
        final PluginManager pluginManager = plugin.getServer().getPluginManager();
        this.combat = new CombatListener(playerRegistry);
        pluginManager.registerEvents(combat, plugin);

        final PermissionChannelBinder alerts =
                new PermissionChannelBinder(Permissions.ALERTS_ENABLE_ON_JOIN, ReactChannels.ALERTS, false);
        final PermissionChannelBinder verbose =
                new PermissionChannelBinder(Permissions.VERBOSE_ENABLE_ON_JOIN, ReactChannels.VERBOSE, false);

        final ConnectionListener connection = new ConnectionListener(
                playerRegistry,
                playerFactory,
                storage,
                joined -> {
                    alerts.accept(joined);
                    verbose.accept(joined);
                },
                quit -> host.quit(quit.getUniqueId())
        );
        pluginManager.registerEvents(connection, plugin);
        for (final Player online : plugin.getServer().getOnlinePlayers()) {
            connection.registerPlayer(online);
        }

        if (PlaceholderAPIUtil.apiPresent()) {
            logger.info("Registering PlaceholderAPI expansion...");
            this.placeholders = new ReactPlaceholderExpansion(
                    plugin.getName(),
                    "g4vrk",
                    plugin.getDescription().getVersion(),
                    playerRegistry,
                    offlinePlayer -> {
                        final String status = host.status();
                        return status == null ? configs.actions().texts().unavailable() : status;
                    }
            );
            placeholders.register();
        }
    }

    public @NotNull CombatListener combat() {
        return combat;
    }

    public void reload() {
        if (combat != null) {
            combat.reload();
        }
    }

    public void close() {
        try {
            if (packetListeners.length > 0) {
                logger.info("Unregistering listeners...");
                PacketEvents.getAPI().getEventManager().unregisterListeners(packetListeners);
            }
        } catch (final Exception ex) {
            logger.error("Could not unregister packet listeners", ex);
        } finally {
            packetListeners = new PacketListenerCommon[0];
        }

        try {
            if (placeholders != null) {
                placeholders.unregister();
            }
        } catch (final Exception ex) {
            logger.error("Could not unregister PlaceholderAPI expansion", ex);
        } finally {
            placeholders = null;
        }

        try {
            ReactChannels.ALERTS.clear();
            ReactChannels.VERBOSE.clear();
        } catch (final Exception ex) {
            logger.error("Could not clear channel recipients", ex);
        }
    }

}
