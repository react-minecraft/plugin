package com.g4vrk.react.fabric.sender;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.NotNull;

/**
 * Adapts a Fabric command source to React's platform-independent sender contract.
 */
public class FabricPlatformSender implements PlatformSender, ForwardingAudience.Single {

    private final CommandSourceStack source;
    private final Audience audience;

    public FabricPlatformSender(
            final @NotNull CommandSourceStack source
    ) {

        this.source = source;
        this.audience = (Audience) source;

    }

    public static @NotNull PlatformSender wrap(
            final @NotNull CommandSourceStack source
    ) {

        return source.getEntity() instanceof final ServerPlayer player
                ? new FabricPlatformPlayer(player)
                : new FabricPlatformSender(source);

    }

    public @NotNull CommandSourceStack commandSource() {

        return source;

    }

    @Override
    public @NotNull String name() {

        return source.getTextName();

    }

    @Override
    public boolean hasPermission(
            final @NotNull String permission
    ) {

        return source.getEntity() == null || source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);

    }

    @Override
    public @NotNull Object handle() {

        return source;

    }

    @Override
    public @NotNull Audience audience() {

        return audience;

    }

    @Override
    public boolean equals(
            final Object other
    ) {

        if (this == other) {
            return true;
        }
        if (!(other instanceof final FabricPlatformSender that)) {
            return false;
        }
        return source.equals(that.source);

    }

    @Override
    public int hashCode() {

        return source.hashCode();

    }

}
