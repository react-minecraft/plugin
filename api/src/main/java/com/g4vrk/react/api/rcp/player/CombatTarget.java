package com.g4vrk.react.api.rcp.player;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-independent combat target type identifiers.
 */
@ApiStatus.AvailableSince("2.0")
public final class CombatTarget {

    /**
     * Resource key of the vanilla training mannequin entity type.
     */
    public static final @NotNull String MANNEQUIN_TYPE_ID = "minecraft:mannequin";

    private CombatTarget() {

    }

    /**
     * Checks whether the given namespaced entity type key identifies a training mannequin.
     *
     * @param entityTypeId the namespaced entity type key (for example {@code minecraft:mannequin}), or {@code null}
     * @return {@code true} when it matches {@link #MANNEQUIN_TYPE_ID}
     */
    public static boolean isMannequinType(
            final @Nullable String entityTypeId
    ) {

        return MANNEQUIN_TYPE_ID.equals(entityTypeId);

    }

    /**
     * Checks whether the given namespace and path identify a training mannequin without string concatenation.
     *
     * @param namespace the resource namespace, or {@code null}
     * @param key       the resource key/path, or {@code null}
     * @return {@code true} when it matches {@link #MANNEQUIN_TYPE_ID}
     */
    @ApiStatus.AvailableSince("2.0")
    public static boolean isMannequinType(
            final @Nullable String namespace,
            final @Nullable String key
    ) {

        return "minecraft".equals(namespace) && "mannequin".equals(key);

    }

}
