package com.g4vrk.react.api.extension;

import com.g4vrk.react.api.event.EventBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The React event bus as one extension sees it, which also takes Bukkit and PacketEvents listeners.
 *
 * <p>{@link #register(Object)} subscribes the {@code @Subscribe} methods of an object, and
 * registers it with Bukkit when it is an {@code org.bukkit.event.Listener} and with
 * PacketEvents when it is a {@code PacketListenerCommon}. One object may be all three.</p>
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface ListenerRegistry extends EventBus {

    /**
     * Registers a listener everywhere it fits.
     *
     * @param listener a React, Bukkit or PacketEvents listener
     */
    @Override
    void register(final @NotNull Object listener);

    /**
     * Removes a listener from everywhere {@link #register(Object)} put it.
     *
     * @param listener the listener
     */
    @Override
    void unregister(final @NotNull Object listener);

}
