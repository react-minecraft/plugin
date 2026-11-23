package com.g4vrk.react.sponge.sender;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;

import java.util.UUID;

/**
 * Adapts a Sponge {@link ServerPlayer} to React's platform-independent player contract.
 */
public final class SpongePlatformPlayer extends SpongePlatformSender implements PlatformPlayer {

    private final ServerPlayer player;
    private final UUID uniqueId;

    public SpongePlatformPlayer(
            final @NotNull ServerPlayer player,
            final @NotNull CommandCause cause
    ) {

        super(player, cause.audience(), cause.subject());
        this.player = player;
        this.uniqueId = player.uniqueId();

    }

    public SpongePlatformPlayer(
            final @NotNull ServerPlayer player
    ) {

        super(player, player, player);
        this.player = player;
        this.uniqueId = player.uniqueId();

    }

    @Override
    public @NotNull String name() {

        return player.name();

    }

    @Override
    public @NotNull UUID uniqueId() {

        return uniqueId;

    }

    @Override
    public int entityId() {

        return PacketEvents.getAPI().getPlayerManager().getUser(player).getEntityId();

    }

    @Override
    public boolean online() {

        return player.isOnline();

    }

    @Override
    public @NotNull ServerPlayer handle() {

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
