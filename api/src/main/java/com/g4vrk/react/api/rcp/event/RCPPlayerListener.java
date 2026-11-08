package com.g4vrk.react.api.rcp.event;

import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.RotationSample;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Player events the plugin passes to an RCP. Every method is optional.
 *
 * <p>Methods run on the thread that produced the event, usually a netty thread for
 * rotations, so an implementation must not block and must move to the server thread
 * itself before it touches the world.</p>
 */
@ApiStatus.AvailableSince("2.0")
public interface RCPPlayerListener {

    /**
     * Called after every rotation the plugin recorded for a player it checks.
     *
     * <p>{@code sample} is a flat array of {@link RotationSample#STRIDE} floats, laid out as
     * {@link RotationSample} documents. The plugin reuses the array for the next rotation, so
     * copy it when the values must outlive the call.</p>
     *
     * @param player the player
     * @param sample the rotation
     */
    default void onRotation(final @NotNull RCPPlayer player, final float @NotNull [] sample) {
    }

    /**
     * Called for every online player after the plugin reloaded its configs.
     */
    default void onReload(final @NotNull RCPPlayer player) {
    }

    /**
     * The player's transient state was reset, for example after a teleport or a world change.
     */
    default void onReset(final @NotNull UUID player) {
    }

    /**
     * The player left the server: everything kept for them can be dropped.
     */
    default void onQuit(final @NotNull UUID player) {
        onReset(player);
    }

}
