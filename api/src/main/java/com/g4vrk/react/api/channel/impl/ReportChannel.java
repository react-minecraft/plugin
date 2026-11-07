package com.g4vrk.react.api.channel.impl;

import com.g4vrk.react.api.channel.CheckReport;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivers {@link CheckReport}s to its recipients.
 *
 * <p>A recipient either follows every player or only the players it was subscribed with.
 * How a report reaches the recipient is up to the {@link Delivery} the plugin installs; React
 * runs the actions from {@code actions.yml}.</p>
 */
public final class ReportChannel extends AudienceChannel<CheckReport> {

    private final Map<Audience, Set<UUID>> targets = new ConcurrentHashMap<>();

    private volatile Delivery delivery = (recipient, report) -> {
    };

    public void delivery(final @NotNull Delivery delivery) {
        this.delivery = delivery;
    }

    /**
     * Subscribes the recipient to reports of every player.
     */
    @Override
    public boolean subscribe(final @NotNull Audience recipient) {
        this.targets.remove(recipient);
        return super.subscribe(recipient);
    }

    /**
     * Subscribes the recipient to reports of the given players only, replacing its previous
     * selection.
     */
    public boolean subscribe(final @NotNull Audience recipient, final @NotNull Collection<UUID> players) {
        this.targets.put(recipient, Set.copyOf(players));
        return super.subscribe(recipient);
    }

    @Override
    public boolean remove(final @NotNull Audience recipient) {
        this.targets.remove(recipient);
        return super.remove(recipient);
    }

    /**
     * The players the recipient follows, or {@code null} when it follows everyone.
     */
    public @Nullable Set<UUID> targets(final @NotNull Audience recipient) {
        return this.targets.get(recipient);
    }

    @Override
    public void clear() {
        this.targets.clear();
        super.clear();
    }

    @Override
    public void publish(final @NotNull CheckReport report) {
        final Delivery delivery = this.delivery;

        for (final Audience recipient : this.recipients()) {
            final Set<UUID> players = this.targets.get(recipient);

            if (players == null || players.contains(report.player())) {
                delivery.deliver(recipient, report);
            }
        }
    }

    @FunctionalInterface
    public interface Delivery {

        void deliver(@NotNull Audience recipient, @NotNull CheckReport report);

    }

}
