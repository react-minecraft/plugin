package com.g4vrk.react.bukkit;

import com.cryptomorin.xseries.XSound;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.entity.Entity;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.bukkit.command.BukkitCommandBridge;
import com.g4vrk.react.bukkit.listener.BukkitListenerBridge;
import com.g4vrk.react.bukkit.placeholder.ReactPlaceholderExpansion;
import com.g4vrk.react.bukkit.reboot.PluginReboot;
import com.g4vrk.react.bukkit.sender.BukkitPlatformPlayer;
import com.g4vrk.react.bukkit.util.BukkitBedrockDetector;
import com.g4vrk.react.placeholder.SimplePlaceholderRegistry;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.update.PlatformUpdateSource;
import com.g4vrk.react.platform.reboot.PluginRebootMessage;
import com.g4vrk.react.platform.reboot.PreparedPluginReboot;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.react.player.registry.PlayerRegistry;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared {@link ReactPlatform} implementation for Bukkit-derived servers (Paper and Folia).
 */
public final class BukkitPlatform implements ReactPlatform {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final String platformName;
    private final String artifactPrefix;

    private @Nullable ReactPlaceholderExpansion placeholderExpansion;

    public BukkitPlatform(
            final @NotNull Plugin plugin,
            final @NotNull Scheduler scheduler,
            final @NotNull String platformName,
            final @NotNull String artifactPrefix
    ) {

        this.plugin = plugin;
        this.scheduler = scheduler;
        this.platformName = platformName;
        this.artifactPrefix = artifactPrefix;

    }

    @Override
    public @NotNull String platformName() {

        return platformName;

    }

    @Override
    public @NotNull String pluginName() {

        return plugin.getName();

    }

    @Override
    public @NotNull String pluginVersion() {

        return plugin.getDescription().getVersion();

    }

    @Override
    public boolean enabled() {

        return plugin.isEnabled();

    }

    @Override
    public @NotNull Path dataDirectory() {

        return plugin.getDataFolder().toPath();

    }

    @Override
    public @NotNull Plugin pluginHandle() {

        return plugin;

    }

    @Override
    public void dispatchConsoleCommand(
            final @NotNull String command
    ) {

        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);

    }

    @Override
    public @NotNull PlatformPlayer adaptPlayer(final @NotNull Object nativePlayer) {

        if (!(nativePlayer instanceof final Player player)) {
            throw new IllegalArgumentException("Expected a Bukkit player");
        }
        return new BukkitPlatformPlayer(player);

    }
    @Override
    public @Nullable PlatformPlayer player(
            final @NotNull UUID uniqueId
    ) {

        final Player player = plugin.getServer().getPlayer(uniqueId);
        return player == null ? null : new BukkitPlatformPlayer(player);

    }

    @Override
    public @Nullable PlatformPlayer player(
            final @NotNull String name
    ) {

        final Player player = plugin.getServer().getPlayerExact(name);
        return player == null ? null : new BukkitPlatformPlayer(player);

    }

    @Override
    public @NotNull Collection<? extends PlatformPlayer> onlinePlayers() {

        final Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        final List<PlatformPlayer> wrapped = new ArrayList<>(online.size());
        for (final Player player : online) {
            wrapped.add(new BukkitPlatformPlayer(player));
        }
        return wrapped;

    }

    @Override
    public @NotNull TaskScheduler<PlatformPlayer> createScheduler() {

        return scheduler.mapEntity(player -> (Entity) player.handle());

    }

    @Override
    public void disableSelf() {

        plugin.getServer().getPluginManager().disablePlugin(plugin);

    }

    @Override
    public @NotNull PlatformCommandBridge createCommands(
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    ) {

        return new BukkitCommandBridge(plugin, scheduler);

    }

    @Override
    public @NotNull PlatformListenerBridge createListeners(
            final @NotNull Logger logger
    ) {

        return new BukkitListenerBridge(plugin, logger);

    }

    @Override
    public @NotNull Key resolveSoundKey(
            final @NotNull String soundName
    ) {

        if (soundName.indexOf(':') < 0 && soundName.indexOf('.') < 0) {
            final Keyed sound = XSound.matchXSound(soundName).map(XSound::parseSound).orElse(null);
            if (sound != null) {
                return Key.key(sound.getKey().toString());
            }
        }
        return Key.key(soundName);

    }

    @Override
    public @NotNull PlatformUpdateSource updateSource(
            final @NotNull Logger logger
    ) {

        Path jar;
        try {
            jar = PluginReboot.findJar(plugin).toPath();
        } catch (final IllegalStateException ex) {
            logger.warn("Plugin updates are off: {}", ex.getMessage());
            jar = null;
        }
        return new PlatformUpdateSource(
                jar,
                plugin.getServer().getUpdateFolderFile().toPath(),
                artifactPrefix,
                plugin.getDescription().getVersion()
        );

    }

    @Override
    public boolean isBedrockPlayer(
            final @NotNull UUID uniqueId
    ) {

        return BukkitBedrockDetector.isBedrockPlayer(uniqueId);

    }

    @Override
    public void registerPlaceholders(
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull SimplePlaceholderRegistry placeholders,
            final @NotNull Supplier<@Nullable String> rcpStatus,
            final @NotNull Logger logger
    ) {

        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        logger.info("Registering PlaceholderAPI expansion...");
        this.placeholderExpansion = new ReactPlaceholderExpansion(
                plugin.getName(),
                "g4vrk",
                plugin.getDescription().getVersion(),
                playerRegistry,
                placeholders,
                offlinePlayer -> {
                    final String status = rcpStatus.get();
                    return status == null ? "" : status;
                }
        );
        this.placeholderExpansion.register();

    }

    @Override
    public void unregisterPlaceholders(
            final @NotNull Logger logger
    ) {

        if (this.placeholderExpansion != null) {
            try {
                this.placeholderExpansion.unregister();
            } finally {
                this.placeholderExpansion = null;
            }
        }

    }

    @Override
    public @NotNull PreparedPluginReboot preparePluginReboot(
            final @NotNull PlatformSender sender,
            final @NotNull PluginRebootMessage messages
    ) {

        final CommandSender bukkitSender = sender.unwrap(CommandSender.class);
        if (bukkitSender == null) {
            throw new IllegalStateException("Unsupported command sender type");
        }
        final PluginReboot reboot = PluginReboot.prepare(plugin, bukkitSender, new PluginReboot.MessageSet(messages.unloaded(), messages.loaded(), messages.failed()));
        return new PreparedPluginReboot() {

            @Override
            public @NotNull String artifactName() {

                return reboot.jar().getName();

            }

            @Override
            public @NotNull String newVersion() {

                return reboot.newVersion();

            }

            @Override
            public void schedule() {

                /*
                 * Run outside the command dispatch: disabling React tears down this command manager.
                 */
                Bukkit.getScheduler().runTask(plugin, reboot);

            }

        };

    }

}
