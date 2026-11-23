package com.g4vrk.react.fabric.listener;

import com.g4vrk.react.api.rcp.player.CombatTarget;
import com.g4vrk.react.fabric.FabricPlatform;
import com.g4vrk.react.fabric.sender.FabricPlatformPlayer;
import com.g4vrk.react.platform.listener.CoreListenerCallback;
import com.g4vrk.react.platform.listener.PlatformListenerBridge;
import com.g4vrk.react.platform.listener.PlatformListenerScope;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Fabric lifecycle, combat, PacketEvents, and extension listener bridge.
 */
public final class FabricListenerBridge implements PlatformListenerBridge {

    private final FabricPlatform platform;
    private final Logger logger;
    private volatile @Nullable CoreListenerCallback callbacks;
    private PacketListenerCommon[] packetListeners = new PacketListenerCommon[0];
    private boolean platformEventsRegistered;

    public FabricListenerBridge(
            final @NotNull FabricPlatform platform,
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
        if (!platformEventsRegistered) {
            registerPlatformEvents();
            platformEventsRegistered = true;
        }
        final PacketListenerCommon rotation = new PacketEventsRotationListener(callbacks);
        this.packetListeners = new PacketListenerCommon[]{rotation};
        PacketEvents.getAPI().getEventManager().registerListeners(packetListeners);
        logger.info("Registered Fabric and PacketEvents listeners");

    }

    @Override
    public synchronized void unregisterCore() {

        callbacks = null;
        if (packetListeners.length == 0) {
            return;
        }
        try {
            PacketEvents.getAPI().getEventManager().unregisterListeners(packetListeners);
        } finally {
            packetListeners = new PacketListenerCommon[0];
        }

    }

    @Override
    public @NotNull PlatformListenerScope createExtensionScope(
            final @NotNull Logger extensionLogger
    ) {

        return new FabricExtensionListenerScope(extensionLogger);

    }

    private void registerPlatformEvents() {

        ServerPlayerEvents.JOIN.register(player -> {
            final CoreListenerCallback current = callbacks;
            if (current != null) {
                current.onJoin(new FabricPlatformPlayer(player));
            }
        });
        ServerPlayerEvents.LEAVE.register(player -> {
            final CoreListenerCallback current = callbacks;
            if (current != null) {
                current.onQuit(new FabricPlatformPlayer(player));
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            final CoreListenerCallback current = callbacks;
            if (current != null) {
                current.onTransientReset(newPlayer.getUUID());
            }
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            final CoreListenerCallback current = callbacks;
            if (current != null) {
                current.onTransientReset(player.getUUID());
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register(this::onDamage);
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed, damageSource) -> {
            if (killer instanceof final ServerPlayer player && isCombatTarget(killed)) {
                final CoreListenerCallback current = callbacks;
                if (current != null) {
                    current.onCombatHit(player.getUUID());
                }
            }
        });

    }

    private void onDamage(
            final LivingEntity entity,
            final DamageSource source,
            final float baseDamage,
            final float damage,
            final boolean blocked
    ) {

        if (blocked || damage <= 0 || !isCombatTarget(entity)) {
            return;
        }
        final Entity attacker = source.getEntity();
        if (attacker instanceof final ServerPlayer player) {
            final CoreListenerCallback current = callbacks;
            if (current != null) {
                current.onCombatHit(player.getUUID());
            }
        }

    }

    private boolean isCombatTarget(
            final @NotNull Entity entity
    ) {

        if (entity instanceof Player) {
            return true;
        }
        return CombatTarget.isMannequinType(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());

    }

}
