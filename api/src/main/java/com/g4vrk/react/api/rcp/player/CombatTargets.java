package com.g4vrk.react.api.rcp.player;

import org.jetbrains.annotations.ApiStatus;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Player-shaped combat targets, without linking newer Bukkit entity classes.
 */
@ApiStatus.AvailableSince("2.0")
public final class CombatTargets {
    private CombatTargets() {}

    public static boolean isSupported(Entity entity) {
        return entity instanceof Player || isMannequin(entity);
    }

    public static boolean isMannequin(Entity entity) {
        return entity != null && "minecraft:mannequin".equals(entity.getType().getKey().toString());
    }
}
