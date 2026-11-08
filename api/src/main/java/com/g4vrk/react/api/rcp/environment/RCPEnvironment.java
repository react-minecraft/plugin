package com.g4vrk.react.api.rcp.environment;

import com.g4vrk.react.api.extension.ExtensionEnvironment;
import com.g4vrk.react.api.rcp.event.RCPPlayerListener;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.registry.RCPPlayerRegistry;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the plugin gives one RCP.
 *
 * <p>Player listeners registered here are removed with the rest of the environment when
 * the RCP is disabled. Registration may happen on any thread.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface RCPEnvironment extends ExtensionEnvironment {

    /**
     * The RCP's {@code rcp-metadata.yml}.
     *
     * @return the metadata
     */
    @NotNull RCPMetadata metadata();

    /**
     * Online players as an RCP sees them.
     *
     * @return the players
     */
    @NotNull RCPPlayerRegistry players();

    /**
     * Hands a result to the plugin's check, which alerts, punishes, stores history and
     * fires events from it as for any other check. Safe from any thread.
     *
     * @param player the player the result is about
     * @param result the result
     */
    void fire(final @NotNull RCPPlayer player, final @NotNull InferenceResult result);

    /**
     * Receives rotations and player state changes.
     *
     * @param listener the listener
     */
    void listen(final @NotNull RCPPlayerListener listener);

    /**
     * Sets what the RCP reports as its state, shown in {@code /react rcps} and in the
     * {@code %react_model%} placeholder.
     *
     * @param status the state, or {@code null} to clear it
     */
    void status(final @Nullable String status);

}
