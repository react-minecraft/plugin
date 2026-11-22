package com.g4vrk.react.extension.environment;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.event.EventDispatcher;
import com.g4vrk.react.api.placeholder.registry.PlaceholderRegistry;
import com.g4vrk.react.api.platform.PlatformServer;
import com.g4vrk.react.api.punish.registry.PunishmentRegistry;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.rcp.RCPHost;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * The core services every extension environment is built from.
 *
 * @param platform     the active platform server facade
 * @param dataFolder   the plugin data directory
 * @param logger       the core logger
 * @param scheduler    the core scheduler
 * @param events       the React event bus
 * @param placeholders the shared placeholder registry
 * @param text         the MiniMessage formatter
 * @param actions      the shared action parser
 * @param commands     the platform command bridge
 * @param listeners    the platform listener bridge
 * @param players      the tracked players
 * @param rcpHost      where RCP environments attach
 * @param punishments  the rules of punishments.yml
 */
public record ExtensionContext(
        @NotNull PlatformServer platform,
        @NotNull Path dataFolder,
        @NotNull Logger logger,
        @NotNull TaskScheduler<PlatformPlayer> scheduler,
        @NotNull EventDispatcher events,
        @NotNull PlaceholderRegistry placeholders,
        @NotNull TextFormatter text,
        @NotNull ActionParser<Audience> actions,
        @NotNull PlatformCommandBridge commands,
        @NotNull PlatformListenerBridge listeners,
        @NotNull PlayerRegistry players,
        @NotNull RCPHost rcpHost,
        @NotNull PunishmentRegistry punishments
) {

    /**
     * A logger scoped to an extension with a distinct name.
     *
     * @param name the extension name
     * @return the logger
     */
    public @NotNull Logger logger(
            final @NotNull String name
    ) {

        if (platform.pluginName().equalsIgnoreCase(name)) {
            return logger;
        }
        return LoggerFactory.getLogger(logger.getName() + "#" + name);

    }

}
