package com.g4vrk.react.api.extension.command;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The command tree of the plugin, as one extension may change it.
 *
 * <p>Commands are built and registered with <a href="https://docs.incendo.org/cloud/">
 * Cloud</a> exactly as the plugin's own commands are, so an extension gets the same argument
 * parsers, suggestions, Brigadier integration and exception handling:</p>
 *
 * <pre>{@code
 * environment.commands().register("link", environment.commands()
 *         .command("link")
 *         .literal("link")
 *         .required("player", PlayerParser.playerParser())
 *         .handler(context -> {
 *             // ...
 *         }));
 * }</pre>
 *
 * <p>Every command built by {@link #command(String, String...)} requires
 * {@code react.command.<label>} until the extension sets another permission itself.</p>
 *
 * <p>A root command the extension creates is removed when the extension is disabled. A command
 * added under an existing root, such as {@code /react}, stays in the tree, because Cloud cannot
 * remove a single node of it; give such a command its own root when it has to vanish on
 * disable.</p>
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
    @NotNull Command.Builder<CommandSender> command(final @NotNull String label, final @NotNull String @NotNull ... aliases);

    /**
     * Registers a built command.
     *
     * @param label   the root of the command, as given to {@link #command(String, String...)}
     * @param command the command
     */
    void register(final @NotNull String label, final @NotNull Command.Builder<CommandSender> command);

    /**
     * Removes every command under a root the extension created.
     *
     * @param label the root
     * @return {@code true} when the root belonged to the extension and was removed
     */
    boolean unregister(final @NotNull String label);

}
