package com.g4vrk.react.minestom;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.reboot.PluginRebootMessage;
import com.g4vrk.react.platform.reboot.PreparedPluginReboot;
import com.g4vrk.react.platform.update.PlatformUpdateSource;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.placeholder.registry.SimplePlaceholderRegistry;
import com.g4vrk.react.minestom.command.MinestomCommandBridge;
import com.g4vrk.react.minestom.listener.MinestomListenerBridge;
import com.g4vrk.react.minestom.sender.MinestomPlatformPlayer;
import com.g4vrk.react.minestom.sender.MinestomPlatformSender;
import com.g4vrk.schedula.api.SchedulaAPI;
import com.g4vrk.schedula.minestom.impl.task.runner.factory.MinestomSchedulerFactory;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Minestom implementation of React's server and runtime boundary.
 */
public final class MinestomPlatform implements ReactPlatform {

    private final Object pluginHandle;
    private final AtomicBoolean active;
    private final Path dataDirectory;
    private final BiPredicate<Player, String> permissionChecker;
    private final TaskScheduler<Entity> scheduler;

    MinestomPlatform(
            final @NotNull Object pluginHandle,
            final @NotNull AtomicBoolean active,
            final @NotNull Path dataDirectory,
            final @NotNull BiPredicate<Player, String> permissionChecker
    ) {

        this.pluginHandle = pluginHandle;
        this.active = active;
        this.dataDirectory = dataDirectory;
        this.permissionChecker = permissionChecker;
        this.scheduler = SchedulaAPI.<Entity>builder()
                .factory(new MinestomSchedulerFactory())
                .build()
                .createScheduler();

    }

    @Override
    public @NotNull String platformName() {

        return "minestom";

    }

    @Override
    public @NotNull String pluginName() {

        return "React";

    }

    @Override
    public @NotNull String pluginVersion() {

        final String version = MinestomRuntime.class.getPackage().getImplementationVersion();
        return version == null ? "unknown" : version;

    }

    @Override
    public boolean enabled() {

        return active.get();

    }

    @Override
    public @NotNull Path dataDirectory() {

        return dataDirectory;

    }

    @Override
    public @NotNull Object pluginHandle() {

        return pluginHandle;

    }

    @Override
    public void dispatchConsoleCommand(
            final @NotNull String command
    ) {

        MinecraftServer.getCommandManager().executeServerCommand(command);

    }

    @Override
    public @NotNull PlatformPlayer adaptPlayer(
            final @NotNull Object nativePlayer
    ) {

        if (!(nativePlayer instanceof final Player player)) {
            throw new IllegalArgumentException("Expected a Minestom player");
        }
        return new MinestomPlatformPlayer(player, permissionChecker);

    }

    @Override
    public @Nullable PlatformPlayer player(
            final @NotNull UUID uniqueId
    ) {

        for (final Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUuid().equals(uniqueId)) {
                return new MinestomPlatformPlayer(player, permissionChecker);
            }
        }
        return null;

    }

    @Override
    public @Nullable PlatformPlayer player(
            final @NotNull String name
    ) {

        for (final Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUsername().equals(name)) {
                return new MinestomPlatformPlayer(player, permissionChecker);
            }
        }
        return null;

    }

    @Override
    public @NotNull Collection<? extends PlatformPlayer> onlinePlayers() {

        final Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        final List<PlatformPlayer> players = new ArrayList<>(online.size());
        for (final Player player : online) {
            players.add(new MinestomPlatformPlayer(player, permissionChecker));
        }
        return players;

    }

    @Override
    public @NotNull TaskScheduler<PlatformPlayer> createScheduler() {

        return scheduler.mapEntity(player -> (Entity) player.handle());

    }

    @Override
    public void disableSelf() {

        throw new UnsupportedOperationException("Minestom does not provide a plugin disable lifecycle");

    }

    @Override
    public @NotNull PlatformCommandBridge createCommands(
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    ) {

        return new MinestomCommandBridge(this);

    }

    @Override
    public @NotNull PlatformListenerBridge createListeners(
            final @NotNull Logger logger
    ) {

        return new MinestomListenerBridge(this, logger);

    }

    @Override
    public @NotNull Key resolveSoundKey(
            final @NotNull String soundName
    ) {

        return Key.key(soundName);

    }

    @Override
    public @NotNull PlatformUpdateSource updateSource(
            final @NotNull Logger logger
    ) {

        return new PlatformUpdateSource(null, dataDirectory.resolve("update"), "react-minestom", pluginVersion());

    }

    @Override
    public void registerPlaceholders(
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull SimplePlaceholderRegistry placeholders,
            final @NotNull Supplier<@Nullable String> rcpStatus,
            final @NotNull Logger logger
    ) {

    }

    @Override
    public void unregisterPlaceholders(
            final @NotNull Logger logger
    ) {

    }

    @Override
    public @NotNull PreparedPluginReboot preparePluginReboot(
            final @NotNull PlatformSender sender,
            final @NotNull PluginRebootMessage messages
    ) {

        throw new UnsupportedOperationException("Minestom does not support unloading React at runtime");

    }

    /**
     * Returns the host-provided permission checker for command sender adapters.
     *
     * @return the permission checker
     */
    @ApiStatus.Internal
    public @NotNull BiPredicate<Player, String> permissionChecker() {

        return permissionChecker;

    }

    /**
     * Adapts a native command sender for the Cloud command bridge.
     *
     * @param sender the native sender
     * @return the platform sender
     */
    @ApiStatus.Internal
    public @NotNull PlatformSender adaptSender(
            final @NotNull CommandSender sender
    ) {

        return MinestomPlatformSender.wrap(sender, permissionChecker);

    }

}
