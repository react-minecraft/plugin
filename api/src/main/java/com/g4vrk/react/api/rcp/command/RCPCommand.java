package com.g4vrk.react.api.rcp.command;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A {@code /react} subcommand an RCP adds.
 *
 * <p>The plugin checks the permission and runs {@link #execute} on the server thread.</p>
 */
public interface RCPCommand {

    String PERMISSION_PREFIX = "react.command.";

    @NotNull String name();

    default @NotNull String permission() {
        return PERMISSION_PREFIX + name();
    }

    void execute(@NotNull CommandSender sender, @NotNull List<String> args);

    default @NotNull List<String> suggest(final @NotNull CommandSender sender, final @NotNull List<String> args) {
        return List.of();
    }

}
