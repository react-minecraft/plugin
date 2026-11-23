package com.g4vrk.react.minestom.listener;

import com.g4vrk.react.minestom.MinestomPlatform;
import com.g4vrk.react.platform.listener.CoreListenerCallback;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.listener.PlatformListenerScope;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.AddEntityToInstanceEvent;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerRespawnEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minestom native event adapter for React core and extension listeners.
 */
public final class MinestomListenerBridge implements PlatformListenerBridge {

    private final MinestomPlatform platform;
    private final Logger logger;
    private final Set<UUID> joinedPlayers = ConcurrentHashMap.newKeySet();

    private volatile CoreListenerCallback callbacks;
    private EventNode<Event> coreNode;

    public MinestomListenerBridge(
            final @NotNull MinestomPlatform platform,
            final @NotNull Logger logger
    ) {

        this.platform = platform;
        this.logger = logger;

    }

    @Override
    public synchronized void registerCore(
            final @NotNull CoreListenerCallback callbacks
    ) {

        unregisterCore();
        this.callbacks = callbacks;
        final EventNode<Event> node = EventNode.all("react-core");
        node.addListener(PlayerSpawnEvent.class, event -> {
            final Player player = event.getPlayer();
            if (joinedPlayers.add(player.getUuid())) {
                callbacks.onJoin(platform.adaptPlayer(player));
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> {
            final Player player = event.getPlayer();
            if (joinedPlayers.remove(player.getUuid())) {
                callbacks.onQuit(platform.adaptPlayer(player));
            }
        });
        node.addListener(PlayerRespawnEvent.class, event -> callbacks.onTransientReset(event.getPlayer().getUuid()));
        node.addListener(AddEntityToInstanceEvent.class, event -> {
            if (event.getEntity() instanceof final Player player) {
                callbacks.onTransientReset(player.getUuid());
            }
        });
        node.addListener(EventListener.builder(PlayerPacketEvent.class)
                .ignoreCancelled(false)
                .handler(event -> {
                    if (event.getPacket() instanceof final ClientPlayerRotationPacket packet) {
                        callbacks.onRotation(event.getPlayer().getUuid(), packet.yaw(), packet.pitch());
                    } else if (event.getPacket() instanceof final ClientPlayerPositionAndRotationPacket packet) {
                        callbacks.onRotation(
                                event.getPlayer().getUuid(),
                                packet.position().yaw(),
                                packet.position().pitch()
                        );
                    }
                })
                .build());
        node.addListener(EntityAttackEvent.class, event -> {
            if (event.getEntity() instanceof final Player player) {
                callbacks.onCombatHit(player.getUuid(), event.getTarget().getEntityId());
            }
        });
        for (final Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            joinedPlayers.add(player.getUuid());
        }
        this.coreNode = node;
        MinecraftServer.getGlobalEventHandler().addChild(node);
        logger.info("Registered React Minestom event listeners");

    }

    @Override
    public synchronized void unregisterCore() {

        final EventNode<Event> node = coreNode;
        coreNode = null;
        callbacks = null;
        if (node != null) {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
        joinedPlayers.clear();

    }

    @Override
    public @NotNull PlatformListenerScope createExtensionScope(
            final @NotNull Logger extensionLogger
    ) {

        return new MinestomExtensionListenerScope(extensionLogger);

    }

}
