package com.g4vrk.react.player;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Tracks the end time of one player's current combat window.
 */
@Getter
@Accessors(fluent = true)
public final class CombatActivity {

    private volatile long activityUntil = 0L;

    public boolean active() {

        return System.currentTimeMillis() < activityUntil;

    }

    public synchronized void extend(
            final long durationTicks
    ) {

        final long now = System.currentTimeMillis();
        final long durationMillis = durationTicks <= 0L
                ? 0L
                : durationTicks > (Long.MAX_VALUE - now) / 50L
                        ? Long.MAX_VALUE - now
                        : durationTicks * 50L;
        final long requestedUntil = now + durationMillis;
        this.activityUntil = Math.max(this.activityUntil, requestedUntil);

    }

    public synchronized void reset() {

        this.activityUntil = 0L;

    }

}
