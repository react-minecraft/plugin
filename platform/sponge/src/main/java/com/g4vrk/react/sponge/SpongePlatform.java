package com.g4vrk.react.sponge;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.sponge.command.SpongeCommandBridge;
import com.g4vrk.react.sponge.listener.SpongeListenerBridge;
import com.g4vrk.react.sponge.sender.SpongePlatformPlayer;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.placeholder.registry.SimplePlaceholderRegistry;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.reboot.PluginRebootMessage;
import com.g4vrk.react.platform.reboot.PreparedPluginReboot;
import com.g4vrk.react.platform.update.PlatformUpdateSource;
import com.g4vrk.react.player.registry.PlayerRegistry;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.Server;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.plugin.PluginContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sponge implementation of React's platform service boundary.
 */
public final class SpongePlatform implements ReactPlatform {

    private final PluginContainer container;
    private final TaskScheduler<ServerPlayer> scheduler;
    private final Path dataDirectory;

    public SpongePlatform(
            final @NotNull PluginContainer container,
            final @NotNull TaskScheduler<ServerPlayer> scheduler
    ) {

        this.container = container;
        this.scheduler = scheduler;
        this.dataDirectory = Sponge.configManager().pluginConfig(container).directory();

    }

    @Override
    public @NotNull String platformName() {

        return "sponge";

    }

    @Override
    public @NotNull String pluginName() {

        return container.metadata().name().orElse("React");

    }

    @Override
    public @NotNull String pluginVersion() {

        return container.metadata().version().toString();

    }

    @Override
    public boolean enabled() {

        return Sponge.isServerAvailable();

    }

    @Override
    public @NotNull Path dataDirectory() {

        return dataDirectory;

    }

    @Override
    public @NotNull Object pluginHandle() {

        return container;

    }

    @Override
    public void dispatchConsoleCommand(
            final @NotNull String command
    ) {

        try {
            Sponge.server().commandManager().process(Sponge.systemSubject(), command);
        } catch (final CommandException exception) {
            container.logger().error("Failed to dispatch console command", exception);
        }

    }

    @Override
    public @NotNull PlatformPlayer adaptPlayer(
            final @NotNull Object nativePlayer
    ) {

        if (!(nativePlayer instanceof final ServerPlayer player)) {
            throw new IllegalArgumentException("Expected a Sponge server player");
        }
        return new SpongePlatformPlayer(player);

    }

    @Override
    public @Nullable PlatformPlayer player(
            final @NotNull UUID uniqueId
    ) {

        return Sponge.server().player(uniqueId)
                .map(SpongePlatformPlayer::new)
                .orElse(null);

    }

    @Override
    public @Nullable PlatformPlayer player(
            final @NotNull String name
    ) {

        return Sponge.server().player(name)
                .map(SpongePlatformPlayer::new)
                .orElse(null);

    }

    @Override
    public @NotNull Collection<? extends PlatformPlayer> onlinePlayers() {

        final Server server = Sponge.server();
        final List<PlatformPlayer> players = new ArrayList<>(server.onlinePlayers().size());
        for (final ServerPlayer player : server.onlinePlayers()) {
            players.add(new SpongePlatformPlayer(player));
        }
        return players;

    }

    @Override
    public @NotNull TaskScheduler<PlatformPlayer> createScheduler() {

        return scheduler.mapEntity(player -> (ServerPlayer) player.handle());

    }

    @Override
    public void disableSelf() {

        throw new UnsupportedOperationException("Sponge does not expose plugin disablement at runtime");

    }

    @Override
    public @NotNull PlatformCommandBridge createCommands(
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    ) {

        return new SpongeCommandBridge(container, this, container.logger(), scheduler);

    }

    @Override
    public @NotNull PlatformListenerBridge createListeners(
            final @NotNull Logger logger
    ) {

        return new SpongeListenerBridge(container, logger);

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

        return new PlatformUpdateSource(null, dataDirectory.resolve("update"), "react-sponge", pluginVersion());

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

        throw new IllegalStateException("Live plugin reboot is not supported on Sponge");

    }

}
