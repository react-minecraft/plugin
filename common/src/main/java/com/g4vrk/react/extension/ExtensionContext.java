package com.g4vrk.react.extension;

import com.g4vrk.react.api.punish.registry.PunishmentRegistry;
import com.g4vrk.react.api.event.EventBus;
import com.g4vrk.react.api.placeholder.registry.PlaceholderRegistry;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * The plugin services every extension environment is built from.
 *
 * @param plugin       the React plugin
 * @param dataFolder   the plugin folder
 * @param logger       the plugin logger
 * @param scheduler    the plugin scheduler
 * @param events       the React event bus
 * @param placeholders the shared placeholder registry
 * @param text         the MiniMessage formatter
 * @param commands     the Cloud command tree of the plugin
 * @param players      the tracked players
 * @param rcpHost      where RCP environments attach
 * @param punishments  the rules of punishments.yml
 */
public record ExtensionContext(
        @NotNull Plugin plugin,
        @NotNull Path dataFolder,
        @NotNull Logger logger,
        @NotNull Scheduler scheduler,
        @NotNull EventBus events,
        @NotNull PlaceholderRegistry placeholders,
        @NotNull TextFormatter text,
        @NotNull CommandManager<CommandSender> commands,
        @NotNull PlayerRegistry players,
        @NotNull RCPHost rcpHost,
        @NotNull PunishmentRegistry punishments
) {

    /**
     * A logger for one extension, named {@code React#<name>}.
     *
     * @param name the extension name
     * @return the logger
     */
    public @NotNull Logger logger(final @NotNull String name) {
        return LoggerFactory.getLogger(logger.getName() + "#" + name);
    }

}
