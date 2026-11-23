package com.g4vrk.react.sponge.listener;

import com.g4vrk.react.platform.listener.CoreListenerCallback;
import com.g4vrk.react.sponge.sender.SpongePlatformPlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.entity.ChangeEntityWorldEvent;
import org.spongepowered.api.event.entity.living.player.RespawnPlayerEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.profile.GameProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forwards Sponge player lifecycle changes into React's session callbacks.
 */
public final class SpongeConnectionListener {

    private final CoreListenerCallback callbacks;
    private final Map<UUID, ServerPlayer> activePlayers = new ConcurrentHashMap<>();

    public SpongeConnectionListener(final @NotNull CoreListenerCallback callbacks) {

        this.callbacks = callbacks;
        if (Sponge.isServerAvailable()) {
            for (final ServerPlayer player : Sponge.server().onlinePlayers()) {
                activePlayers.put(player.uniqueId(), player);
            }
        }

    }

    @Listener(order = Order.LATE)
    public void onJoin(final @NotNull ServerSideConnectionEvent.Join event) {

        final ServerPlayer player = event.player();
        activePlayers.put(player.uniqueId(), player);
        callbacks.onJoin(new SpongePlatformPlayer(player));

    }

    @Listener(order = Order.LATE)
    public void onDisconnect(final @NotNull ServerSideConnectionEvent.Disconnect event) {

        final GameProfile profile = event.profile().orElse(null);
        final UUID uniqueId = profile == null ? null : profile.uuid();
        ServerPlayer player = event.cause().first(ServerPlayer.class).orElse(null);
        if (player == null && uniqueId != null) {
            player = activePlayers.get(uniqueId);
        }
        if (player == null) {
            if (uniqueId != null && Sponge.isServerAvailable()) {
                player = Sponge.server().player(uniqueId).orElse(null);
            }
        }
        if (player != null) {
            try {
                callbacks.onQuit(new SpongePlatformPlayer(player));
            } finally {
                activePlayers.remove(player.uniqueId());
            }
        } else if (uniqueId != null) {
            activePlayers.remove(uniqueId);
        }

    }

    @Listener(order = Order.POST)
    public void onWorldChange(final ChangeEntityWorldEvent.Post event) {

        if (event.entity() instanceof final ServerPlayer player
                && !(event instanceof RespawnPlayerEvent.Post)) {
            callbacks.onTransientReset(player.uniqueId());
        }

    }

    @Listener(order = Order.POST)
    public void onRespawn(final RespawnPlayerEvent.Post event) {

        callbacks.onTransientReset(event.entity().uniqueId());

    }

}
