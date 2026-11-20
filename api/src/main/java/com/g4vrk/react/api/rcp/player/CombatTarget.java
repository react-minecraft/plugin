package com.g4vrk.react.api.rcp.player;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Player-shaped combat targets, without linking newer Bukkit entity classes.
 */
@ApiStatus.AvailableSince("2.0")
public final class CombatTarget {

    private CombatTarget() {

    }

    /**
     * Checks whether the entity is a supported combat target.
     *
     * @param entity the entity, or {@code null}
     * @return whether the entity is a player or mannequin
     */
    public static boolean isSupported(final @Nullable Entity entity) {

        return entity instanceof Player || isMannequin(entity);

    }

    /**
     * Checks for a mannequin without linking to newer Bukkit entity classes.
     *
     * @param entity the entity, or {@code null}
     * @return whether the entity is a mannequin
     */
    public static boolean isMannequin(final @Nullable Entity entity) {

        return entity != null && "minecraft:mannequin".equals(entity.getType().getKey().toString());

    }

}
