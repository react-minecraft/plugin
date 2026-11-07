package com.g4vrk.react.api.rcp.player;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0")
public interface RCPPlayers {

    @Nullable RCPPlayer get(@NotNull UUID uniqueId);

    /**
     * The player whose entity has this id, as packets name it.
     */
    @Nullable RCPPlayer byEntityId(int entityId);

    @NotNull Collection<? extends RCPPlayer> online();

}
