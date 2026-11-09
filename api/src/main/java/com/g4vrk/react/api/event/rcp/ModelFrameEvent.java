package com.g4vrk.react.api.event.rcp;

import com.g4vrk.react.api.event.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A dataset frame produced by an RCP, without exposing that RCP's implementation classes.
 *
 * <p>Subscribe through {@code environment.listeners().subscribe(ModelFrameEvent.class, handler)}.
 * The subscription is removed with the extension. Delivery is synchronous on the producer's
 * thread (normally the player's entity thread); handlers must not block or access other entities.
 * Feature names exclude the dataset label and retain the producer's column order. Consumers
 * should check {@link #model()} and the schema before accepting frames.</p>
 */
@ApiStatus.AvailableSince("2.0")
public final class ModelFrameEvent extends Event {
    private final String model;
    private final UUID playerId;
    private final List<String> columns;
    private final float[] features;
    private final boolean inCombat;

    /**
     * Creates a frame. An immutable {@code columns} list ({@link List#of}) is kept as is, not copied.
     *
     * @param model stable model identifier, not its display name
     * @param playerId player whose input produced the frame
     * @param columns ordered dataset feature names, excluding the label
     * @param features values in the same order as columns; the event takes the array over,
     *                 so the producer must not change it afterwards
     * @param inCombat whether the producer's combat window is active
     */
    public ModelFrameEvent(final @NotNull String model, final @NotNull UUID playerId,
                           final @NotNull List<String> columns, final @NotNull float[] features,
                           final boolean inCombat) {
        this.model = Objects.requireNonNull(model, "model");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.columns = List.copyOf(columns);
        if (this.columns.size() != features.length) {
            throw new IllegalArgumentException("Feature count must match the model schema");
        }
        this.features = features;
        this.inCombat = inCombat;
    }

    /** @return stable model identifier */
    public @NotNull String model() { return this.model; }

    /** @return player UUID */
    public @NotNull UUID playerId() { return this.playerId; }

    /** @return immutable ordered feature names */
    public @NotNull List<String> columns() { return this.columns; }

    /** @return independent copy of the dataset values */
    public @NotNull float[] features() { return this.features.clone(); }

    /**
     * Gives one value without copying the frame.
     *
     * @param index position in {@link #columns()}
     * @return the value
     */
    public float feature(final int index) { return this.features[index]; }

    /** @return whether the producer considers the player in combat */
    public boolean inCombat() { return this.inCombat; }
}
