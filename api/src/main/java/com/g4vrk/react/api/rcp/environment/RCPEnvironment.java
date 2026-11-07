package com.g4vrk.react.api.rcp.environment;

import com.g4vrk.react.api.rcp.argument.RCPArgument;
import com.g4vrk.react.api.rcp.event.RCPPlayerListener;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.RCPPlayers;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Everything the plugin gives one RCP.
 *
 * <p>Listeners, packet listeners and arguments registered here are removed by the plugin
 * when the RCP is disabled, so an RCP never unregisters them itself. Registration may
 * happen on any thread: the plugin moves it to the server thread when it has to.</p>
 */
@ApiStatus.AvailableSince("2.0")
public interface RCPEnvironment {

    /**
     * The RCP's own folder, {@code .rcp/<name>}. It exists when the RCP is created.
     */
    @NotNull Path directory();

    @NotNull RCPMetadata metadata();

    @NotNull Logger logger();

    @NotNull Plugin plugin();

    @NotNull Scheduler scheduler();

    @NotNull RCPPlayers players();

    /**
     * Hands a result to the plugin's check, which alerts, punishes, stores history and
     * fires events from it as for any other check. Safe from any thread.
     */
    void fire(@NotNull RCPPlayer player, @NotNull InferenceResult result);

    void listen(@NotNull RCPPlayerListener listener);

    /**
     * Registers a PacketEvents listener: {@code listener} must be a
     * {@code com.github.retrooper.packetevents.event.PacketListenerCommon}.
     *
     * @throws IllegalStateException when PacketEvents is not installed
     */
    void registerPacketListener(@NotNull Object listener);

    /**
     * Adds {@code /react <name>}. Names of the plugin's own arguments and of other RCPs are taken.
     */
    void registerArgument(@NotNull String name, @NotNull RCPArgument argument);

    /**
     * Sets what the RCP reports as its state, shown in {@code /react rcps} and in the
     * {@code %react_model%} placeholder. Null clears it.
     */
    void status(@Nullable String status);

}
