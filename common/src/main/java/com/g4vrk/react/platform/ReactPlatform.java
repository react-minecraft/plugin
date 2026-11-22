package com.g4vrk.react.platform;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.update.PlatformUpdateSource;
import com.g4vrk.react.platform.reboot.PluginRebootMessage;
import com.g4vrk.react.platform.reboot.PreparedPluginReboot;
import com.g4vrk.react.api.platform.PlatformServer;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.placeholder.SimplePlaceholderRegistry;
import com.g4vrk.react.player.registry.PlayerRegistry;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Service Provider Interface implemented by every server platform that hosts React
 * (Paper, Folia, Fabric, Minestom, Sponge, etc.).
 *
 * <p>All platform-specific APIs stay behind this boundary so {@code :api} and {@code :common}
 * remain pure Java.</p>
 */
public interface ReactPlatform extends PlatformServer {

    /**
     * Creates React's platform-independent scheduler for global, async, and entity tasks.
     *
     * @return the scheduler
     */
    @NotNull TaskScheduler<PlatformPlayer> createScheduler();

    /**
     * Disables or unloads React on this platform when startup cannot continue.
     */
    void disableSelf();

    /**
     * Creates the platform command adapter once the scheduler is available.
     *
     * @param scheduler the core scheduler
     * @return the command adapter
     */
    @NotNull PlatformCommandBridge createCommands(
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    );

    /**
     * Creates the platform listener adapter.
     *
     * @param logger the core logger
     * @return the listener adapter
     */
    @NotNull PlatformListenerBridge createListeners(
            final @NotNull Logger logger
    );

    /**
     * Resolves a configured sound identifier into an Adventure {@link Key}.
     *
     * <p>Platforms may override this to support legacy enum names (for example via XSeries on Bukkit).</p>
     *
     * @param soundName the configured sound identifier
     * @return the Adventure sound key
     */
    default @NotNull Key resolveSoundKey(
            final @NotNull String soundName
    ) {

        return Key.key(soundName);

    }

    /**
     * Returns self-update metadata for this platform distribution.
     *
     * @param logger the core logger
     * @return the update source metadata
     */
    @NotNull PlatformUpdateSource updateSource(
            final @NotNull Logger logger
    );

    /**
     * Checks whether a player is connecting through Bedrock (for example Floodgate/Geyser).
     *
     * @param uniqueId the player UUID
     * @return {@code true} when detected as a Bedrock player
     */
    default boolean isBedrockPlayer(
            final @NotNull UUID uniqueId
    ) {

        return false;

    }

    /**
     * Registers optional platform placeholder bridges (for example PlaceholderAPI on Bukkit).
     *
     * @param playerRegistry the tracked player registry
     * @param placeholders   React's shared placeholder registry
     * @param rcpStatus      supplier for the active RCP status text
     * @param logger         the core logger
     */
    default void registerPlaceholders(
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull SimplePlaceholderRegistry placeholders,
            final @NotNull Supplier<@Nullable String> rcpStatus,
            final @NotNull Logger logger
    ) {

    }

    /**
     * Unregisters any placeholder bridge created by {@link #registerPlaceholders}.
     *
     * @param logger the core logger
     */
    default void unregisterPlaceholders(
            final @NotNull Logger logger
    ) {

    }

    /**
     * Prepares a live plugin reboot when supported by the hosting server platform.
     *
     * @param sender   the sender that requested the reboot
     * @param messages preformatted status messages
     * @return the prepared reboot
     * @throws IllegalStateException when live reboot is unsupported or refused
     */
    default @NotNull PreparedPluginReboot preparePluginReboot(
            final @NotNull PlatformSender sender,
            final @NotNull PluginRebootMessage messages
    ) {

        throw new IllegalStateException("Live plugin reboot is not supported on " + platformName());

    }

}
