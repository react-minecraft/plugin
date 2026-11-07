package com.g4vrk.react.api.event;

import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.TrackedPlayerImpl;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class InferenceHistoryEntryAddedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TrackedPlayerImpl player;
    private final InferenceHistoryEntry entry;

    public InferenceHistoryEntryAddedEvent(
            @NotNull TrackedPlayerImpl player,
            @NotNull InferenceHistoryEntry entry
    ) {
        this.player = player;
        this.entry = entry;
    }

    public @NotNull TrackedPlayerImpl player() {
        return player;
    }

    public @NotNull InferenceHistoryEntry entry() {
        return entry;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
