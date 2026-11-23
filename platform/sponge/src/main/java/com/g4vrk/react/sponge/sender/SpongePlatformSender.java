package com.g4vrk.react.sponge.sender;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.service.permission.Subject;

/**
 * Adapts a Sponge command cause to React's platform-independent sender contract.
 */
public class SpongePlatformSender implements PlatformSender, ForwardingAudience.Single {

    private final Object handle;
    private final Audience recipient;
    private final Subject subject;

    public SpongePlatformSender(
            final @NotNull Object handle,
            final @NotNull Audience recipient,
            final @NotNull Subject subject
    ) {

        this.handle = handle;
        this.recipient = recipient;
        this.subject = subject;

    }

    public static @NotNull PlatformSender wrap(
            final @NotNull CommandCause cause
    ) {

        final Object root = cause.root();
        return root instanceof final ServerPlayer player
                ? new SpongePlatformPlayer(player, cause)
                : new SpongePlatformSender(root, cause.audience(), cause.subject());

    }

    @Override
    public @NotNull String name() {

        return subject.friendlyIdentifier().orElseGet(subject::identifier);

    }

    @Override
    public boolean hasPermission(
            final @NotNull String permission
    ) {

        return subject.hasPermission(permission);

    }

    @Override
    public @NotNull Object handle() {

        return handle;

    }

    @Override
    public @NotNull Audience audience() {

        return recipient;

    }

    @Override
    public boolean equals(
            final Object other
    ) {

        if (this == other) {
            return true;
        }
        if (!(other instanceof final SpongePlatformSender that)) {
            return false;
        }
        return handle.equals(that.handle);

    }

    @Override
    public int hashCode() {

        return handle.hashCode();

    }

}
