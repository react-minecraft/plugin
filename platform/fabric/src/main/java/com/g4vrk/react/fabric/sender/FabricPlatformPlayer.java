package com.g4vrk.react.fabric.sender;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import net.kyori.adventure.audience.Audience;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Adapts a Fabric server player to React's platform-independent player contract.
 */
public final class FabricPlatformPlayer extends FabricPlatformSender implements PlatformPlayer {

    private final ServerPlayer player;
    private final UUID uniqueId;

    public FabricPlatformPlayer(
            final @NotNull ServerPlayer player
    ) {

        super(player.createCommandSourceStack());
        this.player = player;
        this.uniqueId = player.getUUID();

    }

    @Override
    public @NotNull UUID uniqueId() {

        return uniqueId;

    }

    @Override
    public int entityId() {

        return player.getId();

    }

    @Override
    public boolean online() {

        return !player.isRemoved() && player.connection != null && player.connection.isAcceptingMessages();

    }

    @Override
    public @NotNull ServerPlayer handle() {

        return player;

    }

    @Override
    public @NotNull Audience audience() {

        return (Audience) player;

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
