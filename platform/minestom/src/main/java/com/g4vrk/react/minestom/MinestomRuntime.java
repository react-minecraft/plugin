package com.g4vrk.react.minestom;

import com.g4vrk.react.extension.library.RuntimeLibraryLoader;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

/**
 * Embeds the React runtime into an already initialized Minestom server.
 *
 * <p>Minestom is a server library and does not provide an official plugin lifecycle. The host must
 * retain this object and close it during its own shutdown sequence.</p>
 */
@ApiStatus.Experimental
public final class MinestomRuntime implements AutoCloseable {

    private static boolean started;

    private final AtomicBoolean active = new AtomicBoolean(true);
    private Runnable[] lifecycle;
    private boolean closed;

    private MinestomRuntime() {

    }


    /**
     * Initializes and loads React with player permissions denied by default.
     *
     * @param dataDirectory the directory owned by the host for React data
     * @return the active React runtime handle
     * @throws IllegalStateException if React was already started or Minestom is not initialized
     */
    public static @NotNull MinestomRuntime start(
            final @NotNull Path dataDirectory
    ) {

        return start(dataDirectory, (player, permission) -> false);

    }

    /**
     * Initializes and loads React using a host-provided player permission check.
     *
     * @param dataDirectory     the directory owned by the host for React data
     * @param permissionChecker checks a Minestom player against a permission node
     * @return the active React runtime handle
     * @throws IllegalStateException if React was already started or Minestom is not initialized
     */
    public static synchronized @NotNull MinestomRuntime start(
            final @NotNull Path dataDirectory,
            final @NotNull BiPredicate<Player, String> permissionChecker
    ) {

        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(permissionChecker, "permissionChecker");
        if (started) {
            throw new IllegalStateException("React can only be started once in a Minestom process");
        }
        started = true;

        final MinestomRuntime runtime = new MinestomRuntime();
        try {
            final URL codeSource = MinestomRuntime.class.getProtectionDomain().getCodeSource().getLocation();
            runtime.lifecycle = RuntimeLibraryLoader.start(
                    codeSource,
                    MinestomRuntime.class.getClassLoader(),
                    dataDirectory,
                    message -> System.getLogger("React").log(System.Logger.Level.WARNING, message),
                    "com.g4vrk.react.minestom.MinestomRuntimeBootstrap",
                    Object[].class,
                    new Object[]{dataDirectory, permissionChecker, runtime.active, runtime},
                    null,
                    Set.of(),
                    Set.of()
            );
            runtime.lifecycle[0].run();
            return runtime;
        } catch (final RuntimeException | Error failure) {
            try {
                runtime.close();
            } catch (final RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            started = false;
            throw failure;
        }

    }

    /**
     * Returns whether this runtime has not yet been closed.
     *
     * @return {@code true} while React is active
     */
    public boolean enabled() {

        return active.get();

    }

    /**
     * Terminates React and removes its event listeners and scheduled work.
     */
    @Override
    public synchronized void close() {

        if (closed) {
            return;
        }
        closed = true;
        final Runnable[] current = lifecycle;
        lifecycle = null;
        try {
            if (current != null) {
                current[1].run();
            }
        } finally {
            active.set(false);
        }

    }

}
