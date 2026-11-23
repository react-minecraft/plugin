package com.g4vrk.react.minestom;

import com.g4vrk.react.React;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

/**
 * Bootstraps React inside the isolated runtime class loader.
 */
@ApiStatus.Internal
public final class MinestomRuntimeBootstrap {

    private MinestomRuntimeBootstrap() {

    }

    /**
     * Initializes React with state supplied by the host-facing runtime handle.
     *
     * @param arguments host data directory, permission checker, active state and runtime handle
     * @return React load and termination callbacks
     */
    @SuppressWarnings("unchecked")
    public static @NotNull Runnable[] initialize(
            final @NotNull Object[] arguments
    ) {

        if (arguments.length != 4
                || !(arguments[0] instanceof final Path dataDirectory)
                || !(arguments[1] instanceof final BiPredicate<?, ?> rawPermissionChecker)
                || !(arguments[2] instanceof final AtomicBoolean active)
                || arguments[3] == null) {
            throw new IllegalArgumentException("Invalid Minestom React bootstrap arguments");
        }
        final BiPredicate<Player, String> permissionChecker = (BiPredicate<Player, String>) rawPermissionChecker;
        final Object pluginHandle = arguments[3];
        final React react = React.INSTANCE;
        react.initialize(new MinestomPlatform(pluginHandle, active, dataDirectory, permissionChecker));
        return new Runnable[]{react::load, () -> {
            try {
                react.terminate();
            } finally {
                active.set(false);
            }
        }};

    }

}
