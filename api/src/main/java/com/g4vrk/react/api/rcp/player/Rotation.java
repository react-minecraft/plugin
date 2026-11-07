package com.g4vrk.react.api.rcp.player;

/**
 * One head rotation of a player, as the plugin recorded it from a movement packet.
 *
 * <p>{@code y} is the pitch and {@code x} the yaw. The deltas, jerks and GCD errors are
 * measured against the previous rotation of the same player.</p>
 */
public record Rotation(
        float y,
        float x,
        float deltaY,
        float deltaX,
        float jerkY,
        float jerkX,
        float gcdErrorY,
        float gcdErrorX
) {
}
