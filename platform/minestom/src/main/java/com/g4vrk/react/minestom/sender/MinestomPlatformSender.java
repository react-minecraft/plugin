package com.g4vrk.react.minestom.sender;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.command.ServerSender;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Adapts a Minestom command sender to React's platform-independent sender contract.
 */
public class MinestomPlatformSender implements PlatformSender, ForwardingAudience.Single {

    private final CommandSender handle;
    private final BiPredicate<Player, String> permissionChecker;

    public MinestomPlatformSender(
            final @NotNull CommandSender handle,
            final @NotNull BiPredicate<Player, String> permissionChecker
    ) {

        this.handle = Objects.requireNonNull(handle, "handle");
        this.permissionChecker = Objects.requireNonNull(permissionChecker, "permissionChecker");

    }

    /**
     * Wraps a Minestom command sender, returning a player wrapper when applicable.
     *
     * @param sender            the native sender
     * @param permissionChecker the host-provided permission checker
     * @return the platform sender
     */
    public static @NotNull PlatformSender wrap(
            final @NotNull CommandSender sender,
            final @NotNull BiPredicate<Player, String> permissionChecker
    ) {

        return sender instanceof final Player player
                ? new MinestomPlatformPlayer(player, permissionChecker)
                : new MinestomPlatformSender(sender, permissionChecker);

    }

    @Override
    public @NotNull String name() {

        if (handle instanceof final Player player) {
            return player.getUsername();
        }
        if (handle instanceof ConsoleSender) {
            return "Console";
        }
        if (handle instanceof ServerSender) {
            return "Server";
        }
        return handle.getClass().getSimpleName();

    }

    @Override
    public boolean hasPermission(
            final @NotNull String permission
    ) {

        return !(handle instanceof final Player player) || permissionChecker.test(player, permission);

    }

    @Override
    public @NotNull CommandSender handle() {

        return handle;

    }

    @Override
    public @NotNull Audience audience() {

        return handle;

    }

    @Override
    public boolean equals(
            final Object other
    ) {

        if (this == other) {
            return true;
        }
        if (!(other instanceof final MinestomPlatformSender that)) {
            return false;
        }
        return handle.equals(that.handle);

    }

    @Override
    public int hashCode() {

        return handle.hashCode();

    }

}
