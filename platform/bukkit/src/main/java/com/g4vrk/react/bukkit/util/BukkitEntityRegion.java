package com.g4vrk.react.bukkit.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * Keeps entity and nearby-chunk reads on the Bukkit or Folia region that owns the player.
 */
public final class BukkitEntityRegion {

    private static final double RANGE = 6.0D;
    private static final @Nullable MethodHandle ENTITY_OWNER = ownershipMethod(Entity.class);
    private static final @Nullable MethodHandle CHUNK_OWNER = ownershipMethod(Location.class, int.class);

    private BukkitEntityRegion() {

    }

    public static boolean owns(final @NotNull Entity entity) {

        if (ENTITY_OWNER == null) {
            return Bukkit.isPrimaryThread();
        }
        try {
            return (boolean) ENTITY_OWNER.invokeExact(entity);
        } catch (final RuntimeException | Error failure) {
            throw failure;
        } catch (final Throwable failure) {
            throw new IllegalStateException("Cannot check entity ownership", failure);
        }

    }

    public static @NotNull List<Entity> nearby(final @NotNull Player player) {

        final List<Entity> entities = nearbyIfOwned(player);
        return entities != null ? entities : List.of();

    }

    public static @Nullable List<Entity> nearbyIfOwned(final @NotNull Player player) {

        return ownsNearbyChunks(player) ? player.getNearbyEntities(RANGE, RANGE, RANGE) : null;

    }

    public static boolean ownsNearbyChunks(final @NotNull Player player) {

        if (CHUNK_OWNER == null) {
            return Bukkit.isPrimaryThread();
        }
        try {
            return (boolean) CHUNK_OWNER.invokeExact(player.getLocation(), 1);
        } catch (final RuntimeException | Error failure) {
            throw failure;
        } catch (final Throwable failure) {
            throw new IllegalStateException("Cannot check nearby chunk ownership", failure);
        }

    }

    private static @Nullable MethodHandle ownershipMethod(final @NotNull Class<?> @NotNull ... parameters) {

        try {
            return MethodHandles.publicLookup().findStatic(
                    Bukkit.class,
                    "isOwnedByCurrentRegion",
                    MethodType.methodType(boolean.class, parameters)
            );
        } catch (final NoSuchMethodException ignored) {
            return null;
        } catch (final IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access Bukkit region ownership checks", failure);
        }

    }

}
