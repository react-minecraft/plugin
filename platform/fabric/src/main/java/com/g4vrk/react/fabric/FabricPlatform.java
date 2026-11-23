package com.g4vrk.react.fabric;

import com.g4vrk.react.React;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.fabric.command.FabricCommandBridge;
import com.g4vrk.react.fabric.listener.FabricListenerBridge;
import com.g4vrk.react.fabric.sender.FabricPlatformPlayer;
import com.g4vrk.react.placeholder.registry.SimplePlaceholderRegistry;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.reboot.PluginRebootMessage;
import com.g4vrk.react.platform.reboot.PreparedPluginReboot;
import com.g4vrk.react.platform.update.PlatformUpdateSource;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.kyori.adventure.key.Key;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
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
 * Fabric implementation of React's platform service boundary.
 */
public final class FabricPlatform implements ReactPlatform {

    private final FabricLoader fabricLoader;
    private final TaskScheduler<Entity> scheduler;
    private final Path dataDirectory;
    private volatile @Nullable MinecraftServer server;
    private @Nullable FabricCommandBridge commandBridge;

    public FabricPlatform(
            final @NotNull FabricLoader fabricLoader,
            final @NotNull TaskScheduler<Entity> scheduler
    ) {

        this.fabricLoader = fabricLoader;
        this.scheduler = scheduler;
        this.dataDirectory = fabricLoader.getConfigDir().resolve("react");

    }

    @Override
    public @NotNull String platformName() {

        return "fabric";

    }

    @Override
    public @NotNull String pluginName() {

        return mod().getMetadata().getName();

    }

    @Override
    public @NotNull String pluginVersion() {

        return mod().getMetadata().getVersion().getFriendlyString();

    }

    @Override
    public boolean enabled() {

        return server != null;

    }

    @Override
    public @NotNull Path dataDirectory() {

        return dataDirectory;

    }

    @Override
    public @NotNull Object pluginHandle() {

        return mod();

    }

    @Override
    public void dispatchConsoleCommand(
            final @NotNull String command
    ) {

        final MinecraftServer activeServer = server();
        activeServer.getCommands().performPrefixedCommand(activeServer.createCommandSourceStack(), command);

    }

    @Override
    public @NotNull PlatformPlayer adaptPlayer(
            final @NotNull Object nativePlayer
    ) {

        if (!(nativePlayer instanceof final ServerPlayer player)) {
            throw new IllegalArgumentException("Expected a Fabric server player");
        }
        return new FabricPlatformPlayer(player);

    }

    @Override
    public @Nullable PlatformPlayer player(
            final @NotNull UUID uniqueId
    ) {

        final ServerPlayer player = server().getPlayerList().getPlayer(uniqueId);
        return player == null ? null : new FabricPlatformPlayer(player);

    }

    @Override
    public @Nullable PlatformPlayer player(
            final @NotNull String name
    ) {

        final ServerPlayer player = server().getPlayerList().getPlayerByName(name);
        return player == null ? null : new FabricPlatformPlayer(player);

    }

    @Override
    public @NotNull Collection<? extends PlatformPlayer> onlinePlayers() {

        final List<ServerPlayer> online = server().getPlayerList().getPlayers();
        final List<PlatformPlayer> wrapped = new ArrayList<>(online.size());
        for (final ServerPlayer player : online) {
            wrapped.add(new FabricPlatformPlayer(player));
        }
        return wrapped;

    }

    @Override
    public @NotNull TaskScheduler<PlatformPlayer> createScheduler() {

        return scheduler.mapEntity(player -> (Entity) player.handle());

    }

    @Override
    public void disableSelf() {

        React.INSTANCE.terminate();

    }

    @Override
    public @NotNull PlatformCommandBridge createCommands(
            final @NotNull TaskScheduler<PlatformPlayer> taskScheduler
    ) {

        this.commandBridge = new FabricCommandBridge(this, taskScheduler);
        return commandBridge;

    }

    @Override
    public @NotNull PlatformListenerBridge createListeners(
            final @NotNull Logger logger
    ) {

        return new FabricListenerBridge(this, logger);

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

        return new PlatformUpdateSource(null, dataDirectory.resolve("update"), "react-fabric", pluginVersion());

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
    public @NotNull PreparedPluginReboot preparePluginReboot(
            final @NotNull PlatformSender sender,
            final @NotNull PluginRebootMessage messages
    ) {

        throw new IllegalStateException("Live mod reboot is not supported on Fabric");

    }

    void server(
            final @NotNull MinecraftServer server
    ) {

        this.server = server;

    }

    void registerCommands() {

        final FabricCommandBridge commands = commandBridge;
        if (commands != null) {
            commands.registerExistingCommands();
        }

    }

    private @NotNull ModContainer mod() {

        return fabricLoader.getModContainer("react").orElseThrow(() -> new IllegalStateException("React mod container is unavailable"));

    }

    private @NotNull MinecraftServer server() {

        final MinecraftServer activeServer = server;
        if (activeServer == null) {
            throw new IllegalStateException("Fabric server has not started");
        }
        return activeServer;

    }

    /**
     * Returns the active server to internal Fabric bridges.
     *
     * @return the active Minecraft server
     */
    @ApiStatus.Internal
    public @NotNull MinecraftServer activeServer() {

        return server();

    }

}
