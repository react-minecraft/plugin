package com.g4vrk.react.api.platform;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Platform-neutral game state used by aim inference models.
 *
 * @param playerX             the player's feet position on the X axis
 * @param playerY             the player's feet position on the Y axis
 * @param playerZ             the player's feet position on the Z axis
 * @param eyeHeight           the player's eye height above the feet position
 * @param targetValid         whether the attacked target is still in the player's world
 * @param targetMinX          the target bounding box minimum X
 * @param targetMinY          the target bounding box minimum Y
 * @param targetMinZ          the target bounding box minimum Z
 * @param targetMaxX          the target bounding box maximum X
 * @param targetMaxY          the target bounding box maximum Y
 * @param targetMaxZ          the target bounding box maximum Z
 * @param targetsInRange      the number of nearby living combat targets
 * @param mainHandItemId      the namespaced key of the item in the player's main hand
 * @param mainHandItemEdible  whether the item can be eaten
 * @param mainHandItemBlock   whether the item places a block
 * @param ping                the player's current network latency in milliseconds
 * @param clientProtocol      the client's protocol number, or {@code -1} when unavailable
 */
@ApiStatus.AvailableSince("2.0")
public record AimSnapshot(
        double playerX,
        double playerY,
        double playerZ,
        double eyeHeight,
        boolean targetValid,
        double targetMinX,
        double targetMinY,
        double targetMinZ,
        double targetMaxX,
        double targetMaxY,
        double targetMaxZ,
        int targetsInRange,
        @NotNull String mainHandItemId,
        boolean mainHandItemEdible,
        boolean mainHandItemBlock,
        float ping,
        int clientProtocol
) {

    public AimSnapshot {

        if (targetsInRange < 0) {
            throw new IllegalArgumentException("targetsInRange cannot be negative");
        }
        if (!Float.isFinite(ping) || ping < 0.0f) {
            throw new IllegalArgumentException("ping must be finite and non-negative");
        }
        if (clientProtocol < -1) {
            throw new IllegalArgumentException("clientProtocol cannot be less than -1");
        }
        mainHandItemId = Objects.requireNonNull(mainHandItemId, "mainHandItemId");

    }

}
