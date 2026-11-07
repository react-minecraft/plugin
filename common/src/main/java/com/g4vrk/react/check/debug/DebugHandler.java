package com.g4vrk.react.check.debug;

import com.g4vrk.react.React;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.player.TrackedPlayerImpl;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

@RequiredArgsConstructor
public class DebugHandler {

    private final Check check;

    private final Logger logger = React.INSTANCE.logger();

    public final void debug(
            final @NotNull String message
    ) {
        logger.info(format(message, check.player()));
    }

    public final void debugToSender(
            final @NotNull Audience sender,
            final @NotNull String message
    ) {
        debug(message);
        sender.sendMessage(Component.text(format(message, check.player())).color(NamedTextColor.GRAY));
    }

    private @NotNull String format(
            final @NotNull String message,
            final @NotNull TrackedPlayerImpl player
    ) {
        return "debug(" + player.name() + ") -> " + check.name() + ": " + message;
    }

}
