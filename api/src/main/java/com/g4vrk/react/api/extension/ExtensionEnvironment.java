package com.g4vrk.react.api.extension;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.placeholder.registry.PlaceholderRegistry;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * What the plugin gives every RCP and addon.
 *
 * <p>Commands, listeners and placeholders registered here belong to the extension: the
 * plugin removes all of them when the extension is disabled or rebooted, so an extension
 * never has to unregister them itself.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface ExtensionEnvironment {

    /**
     * The name from the extension's metadata file.
     *
     * @return the extension name
     */
    @NotNull String name();

    /**
     * The extension's own folder: {@code .rcp/<name>} for an RCP, {@code addons/<name>} for an addon.
     * It exists before the extension is created.
     *
     * @return the data folder
     */
    @NotNull Path directory();

    /**
     * A logger named after the extension.
     *
     * @return the logger
     */
    @NotNull Logger logger();

    /**
     * The React plugin, for Bukkit calls that need a plugin instance.
     *
     * @return the plugin
     */
    @NotNull Plugin plugin();

    /**
     * The plugin's scheduler, which works the same on Paper and Folia.
     *
     * @return the scheduler
     */
    @NotNull Scheduler scheduler();

    /**
     * The Cloud command tree, where the extension registers its commands.
     *
     * @return the command registrar
     */
    @NotNull CommandRegistrar commands();

    /**
     * React events, Bukkit listeners and PacketEvents listeners of this extension.
     *
     * @return the listener registry
     */
    @NotNull ListenerRegistry listeners();

    /**
     * Placeholders that this extension adds to {@code %react_<key>%} and to MiniMessage texts.
     *
     * @return the placeholder registry
     */
    @NotNull PlaceholderRegistry placeholders();

    /**
     * The formatter the plugin uses for its own messages.
     *
     * @return the text formatter
     */
    @NotNull TextFormatter text();

    /**
     * The parser of action lines ({@code [message] ...}, {@code [sound] ...}) the plugin uses,
     * with every built-in action registered.
     *
     * @return the action parser
     */
    @NotNull ActionParser<Audience> actions();

    /**
     * Files in {@link #directory()} with defaults bundled in the extension jar.
     *
     * @return the config factory
     */
    @NotNull ConfigFactory configs();

}
