package com.g4vrk.react.api.extension.command;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.key.CloudKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * The command tree of the plugin, as one extension may change it.
 *
 * <p>Commands are built and registered with <a href="https://docs.incendo.org/cloud/">
 * Cloud</a> against {@link PlatformSender} so extensions work across every supported
 * server platform while preserving native parsers, suggestions, Brigadier integration,
 * and exception handling:</p>
 *
 * <pre>{@code
 * environment.commands().register("react", environment.commands()
 *         .command("react")
 *         .literal("link")
 *         .handler(context -> {
 *             PlatformSender sender = context.sender();
 *             sender.sendMessage(text);
 *         }));
 * }</pre>
 *
 * <p>Every command built by {@link #command(String, String...)} requires
 * {@code react.command.<label>} until the extension sets another permission itself.</p>
 *
 * <p>Commands are removed when the extension is disabled, including commands added under an
 * existing root such as {@code /react}. Commands owned by the plugin or other extensions remain.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface CommandRegistrar {

    /**
     * Starts a command.
     *
     * @param label   the root the command starts with, for example {@code react}
     * @param aliases other names of the root, may be empty
     * @return a builder carrying {@code react.command.<label>}
     */
    @NotNull Command.Builder<PlatformSender> command(
            final @NotNull String label,
            final @NotNull String @NotNull ... aliases
    );

    /**
     * Registers a built command.
     *
     * @param label   the root of the command, as given to {@link #command(String, String...)}
     * @param command the command
     */
    void register(
            final @NotNull String label,
            final @NotNull Command.Builder<PlatformSender> command
    );

    /**
     * Removes this extension's commands under a root, leaving other owners' commands intact.
     *
     * @param label the root
     * @return {@code true} when this extension had commands under the root and they were removed
     */
    boolean unregister(
            final @NotNull String label
    );

    /**
     * Creates a platform-backed single-player argument component.
     *
     * @param key      the Cloud argument key
     * @param required whether the argument is required
     * @return the component builder
     */
    CommandComponent.@NotNull Builder<PlatformSender, PlatformPlayer> singlePlayerArgument(
            final @NotNull CloudKey<PlatformPlayer> key,
            final boolean required
    );

    /**
     * Creates a platform-backed multi-player selector argument component.
     *
     * @param key      the Cloud argument key
     * @param required whether the argument is required
     * @return the component builder
     */
    CommandComponent.@NotNull Builder<PlatformSender, Collection<PlatformPlayer>> multiplePlayersArgument(
            final @NotNull CloudKey<Collection<PlatformPlayer>> key,
            final boolean required
    );

}
