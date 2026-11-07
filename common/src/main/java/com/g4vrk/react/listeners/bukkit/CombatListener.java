package com.g4vrk.react.listeners.bukkit;

import com.g4vrk.react.config.model.MainConfig;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.player.CombatActivity;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.registry.PlayerRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import com.g4vrk.react.api.rcp.player.CombatTargets;
import org.jetbrains.annotations.NotNull;

public class CombatListener implements Listener, ReloadObserver<MainConfig> {

    private final PlayerRegistry playerRegistry;

    private long combatTicks;

    public CombatListener(
            @NotNull PlayerRegistry playerRegistry
    ) {
        this.playerRegistry = playerRegistry;

        this.reload();
    }

    public long combatTicks() {
        return combatTicks;
    }

    public void reload() {
        this.onReload(React.INSTANCE.configs().main());
    }

    @Override
    public void onReload(@NotNull MainConfig config) {
        combatTicks = Math.max(1L, config.player().combat().time().toMillis() / 50L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Player damager) || !CombatTargets.isSupported(event.getEntity())) return;

        final TrackedPlayerImpl localDamager = playerRegistry.get(damager.getUniqueId());

        activate(localDamager);
    }

    /**
     * Also used for packet attacks on invulnerable training mannequins.
     */
    public void activate(TrackedPlayerImpl localDamager) {

        final CombatActivity damagerCombatActivity = localDamager != null ? localDamager.combatActivity : null;

        if (damagerCombatActivity == null) return;

        damagerCombatActivity.extend(combatTicks);
    }
}
