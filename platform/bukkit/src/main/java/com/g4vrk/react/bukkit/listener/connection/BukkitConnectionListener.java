package com.g4vrk.react.bukkit.listener.connection;

import com.g4vrk.react.bukkit.sender.BukkitPlatformPlayer;
import com.g4vrk.react.platform.listener.CoreListenerCallback;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Forwards Bukkit player lifecycle events into React's pure-Java core callbacks.
 */
public final class BukkitConnectionListener implements Listener {

    private final CoreListenerCallback callbacks;

    public BukkitConnectionListener(
            final @NotNull CoreListenerCallback callbacks
    ) {

        this.callbacks = callbacks;

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUserLogin(
            final @NotNull PlayerJoinEvent event
    ) {

        callbacks.onJoin(new BukkitPlatformPlayer(event.getPlayer()));

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUserDisconnect(
            final @NotNull PlayerQuitEvent event
    ) {

        callbacks.onQuit(new BukkitPlatformPlayer(event.getPlayer()));

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(
            final @NotNull PlayerChangedWorldEvent event
    ) {

        callbacks.onTransientReset(event.getPlayer().getUniqueId());

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(
            final @NotNull PlayerRespawnEvent event
    ) {

        callbacks.onTransientReset(event.getPlayer().getUniqueId());

    }

}
