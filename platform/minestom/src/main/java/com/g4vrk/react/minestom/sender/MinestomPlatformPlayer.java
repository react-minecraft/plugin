package com.g4vrk.react.minestom.sender;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Adapts a Minestom {@link Player} to React's platform-independent player contract.
 */
public final class MinestomPlatformPlayer extends MinestomPlatformSender implements PlatformPlayer {

    private final Player player;
    private final UUID uniqueId;

    public MinestomPlatformPlayer(
            final @NotNull Player player,
            final @NotNull BiPredicate<Player, String> permissionChecker
    ) {

        super(player, permissionChecker);
        this.player = player;
        this.uniqueId = player.getUuid();

    }

    @Override
    public @NotNull UUID uniqueId() {

        return uniqueId;

    }

    @Override
    public int entityId() {

        return player.getEntityId();

    }

    @Override
    public boolean online() {

        return MinecraftServer.getConnectionManager().getOnlinePlayers().contains(player);

    }

    @Override
    public @NotNull Player handle() {

        return player;

    }

    @Override
    public boolean equals(
            final Object other
    ) {

        if (this == other) {
            return true;
        }
        if (!(other instanceof final PlatformPlayer that)) {
            return false;
        }
        return uniqueId.equals(that.uniqueId());

    }

    @Override
    public int hashCode() {

        return uniqueId.hashCode();

    }

}
