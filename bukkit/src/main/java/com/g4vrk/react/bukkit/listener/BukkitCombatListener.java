package com.g4vrk.react.bukkit.listener;

import com.g4vrk.react.api.rcp.player.CombatTarget;
import com.g4vrk.react.platform.listener.CoreListenerCallback;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Forwards Bukkit combat hits against supported targets into React's core callbacks.
 */
final class BukkitCombatListener implements Listener {

    private final CoreListenerCallback callbacks;

    BukkitCombatListener(
            final @NotNull CoreListenerCallback callbacks
    ) {

        this.callbacks = callbacks;

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(
            final @NotNull EntityDamageByEntityEvent event
    ) {

        final Entity victim = event.getEntity();
        if (!(event.getDamager() instanceof final Player damager)) {
            return;
        }
        if (victim instanceof Player || CombatTarget.isMannequinType(victim.getType().getKey().toString())) {
            callbacks.onCombatHit(damager.getUniqueId());
        }

    }

}
