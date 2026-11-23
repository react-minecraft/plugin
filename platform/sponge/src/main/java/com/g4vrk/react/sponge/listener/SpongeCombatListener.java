package com.g4vrk.react.sponge.listener;

import com.g4vrk.react.api.rcp.player.CombatTarget;
import com.g4vrk.react.platform.listener.CoreListenerCallback;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.entity.AttackEntityEvent;
import org.spongepowered.api.registry.RegistryTypes;

/**
 * Forwards supported Sponge player attacks into React's combat callback.
 */
public final class SpongeCombatListener {

    private final CoreListenerCallback callbacks;

    public SpongeCombatListener(final @NotNull CoreListenerCallback callbacks) {

        this.callbacks = callbacks;

    }

    @Listener(order = Order.POST)
    public void onAttack(final @NotNull AttackEntityEvent event) {

        if (event.isCancelled()) {
            return;
        }
        if (!(event.entity() instanceof ServerPlayer)
                && !CombatTarget.isMannequinType(event.entity().type().key(RegistryTypes.ENTITY_TYPE).asString())) {
            return;
        }
        event.cause().first(ServerPlayer.class).ifPresent(attacker -> callbacks.onCombatHit(attacker.uniqueId(), -1));

    }

}
