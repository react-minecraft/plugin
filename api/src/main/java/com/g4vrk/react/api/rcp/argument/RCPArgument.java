package com.g4vrk.react.api.rcp.argument;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An argument of {@code /react} that an RCP adds.
 *
 * <p>The name is given at registration: {@code environment.registerArgument("link", argument)}.
 * The plugin checks {@code react.command.<name>} and runs {@link #execute} on the server thread.</p>
 */
public interface RCPArgument {

    String PERMISSION_PREFIX = "react.command.";

    void execute(@NotNull CommandSender sender, @NotNull List<String> args);

    default @NotNull List<String> suggest(final @NotNull CommandSender sender, final @NotNull List<String> args) {
        return List.of();
    }

}
