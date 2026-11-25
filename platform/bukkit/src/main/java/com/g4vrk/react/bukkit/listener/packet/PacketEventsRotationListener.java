package com.g4vrk.react.bukkit.listener.packet;

import com.g4vrk.react.platform.listener.CoreListenerCallback;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.jetbrains.annotations.NotNull;

/**
 * Forwards valid PacketEvents rotation packets into React's core callbacks.
 */
public final class PacketEventsRotationListener extends PacketListenerAbstract {

    private final CoreListenerCallback callbacks;

    public PacketEventsRotationListener(
            final @NotNull CoreListenerCallback callbacks
    ) {

        this.callbacks = callbacks;

    }

    @Override
    public void onPacketReceive(
            final @NotNull PacketReceiveEvent event
    ) {

        if (event.getConnectionState() != ConnectionState.PLAY
                || !WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            return;
        }

        final WrapperPlayClientPlayerFlying movePacket;
        try {
            movePacket = new WrapperPlayClientPlayerFlying(event);
        } catch (final RuntimeException ignored) {
            return;
        }

        if (!movePacket.hasRotationChanged() || event.getUser() == null || event.getUser().getUUID() == null) {
            return;
        }

        final Location location = movePacket.getLocation();
        callbacks.onRotation(event.getUser().getUUID(), location.getYaw(), location.getPitch());

    }

}
