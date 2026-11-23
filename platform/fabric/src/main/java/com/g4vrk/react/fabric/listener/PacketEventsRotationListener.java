package com.g4vrk.react.fabric.listener;

import com.g4vrk.react.platform.listener.CoreListenerCallback;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.jetbrains.annotations.NotNull;

/**
 * Forwards valid PacketEvents player rotation packets to React's check callbacks.
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
        final WrapperPlayClientPlayerFlying packet;
        try {
            packet = new WrapperPlayClientPlayerFlying(event);
        } catch (final RuntimeException ignored) {
            return;
        }
        if (!packet.hasRotationChanged()) {
            return;
        }
        final Location location = packet.getLocation();
        callbacks.onRotation(event.getUser().getUUID(), location.getYaw(), location.getPitch());

    }

}
