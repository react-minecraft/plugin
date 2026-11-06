package com.g4vrk.react.api.rcp.player;

/**
 * One head rotation of a player, as the plugin recorded it from a movement packet.
 *
 * <p>{@code y} is the pitch and {@code x} the yaw. The deltas, jerks and GCD errors are
 * measured against the previous rotation of the same player.</p>
 */
public final class Rotation {

    private final float y;
    private final float x;
    private final float deltaY;
    private final float deltaX;
    private final float jerkY;
    private final float jerkX;
    private final float gcdErrorY;
    private final float gcdErrorX;

    public Rotation(
            final float y,
            final float x,
            final float deltaY,
            final float deltaX,
            final float jerkY,
            final float jerkX,
            final float gcdErrorY,
            final float gcdErrorX
    ) {
        this.y = y;
        this.x = x;
        this.deltaY = deltaY;
        this.deltaX = deltaX;
        this.jerkY = jerkY;
        this.jerkX = jerkX;
        this.gcdErrorY = gcdErrorY;
        this.gcdErrorX = gcdErrorX;
    }

    public float getY() {
        return y;
    }

    public float getX() {
        return x;
    }

    public float getDeltaY() {
        return deltaY;
    }

    public float getDeltaX() {
        return deltaX;
    }

    public float getJerkY() {
        return jerkY;
    }

    public float getJerkX() {
        return jerkX;
    }

    public float getGcdErrorY() {
        return gcdErrorY;
    }

    public float getGcdErrorX() {
        return gcdErrorX;
    }

}
