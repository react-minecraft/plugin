package com.g4vrk.react.bukkit.sender;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Adapts a Bukkit {@link Player} to React's platform-independent {@link PlatformPlayer}.
 */
public final class BukkitPlatformPlayer extends BukkitPlatformSender implements PlatformPlayer {

    private final Player player;
    private final UUID uniqueId;

    public BukkitPlatformPlayer(
            final @NotNull Player player
    ) {

        super(player);
        this.player = player;
        this.uniqueId = player.getUniqueId();

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

        return player.isOnline();

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
