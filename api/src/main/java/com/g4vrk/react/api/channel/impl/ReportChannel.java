package com.g4vrk.react.api.channel.impl;

import com.g4vrk.react.api.channel.CheckReport;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
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
@ApiStatus.Internal
public final class ReportChannel extends AudienceChannel<CheckReport> {

    private final Map<Audience, Set<UUID>> targets = new ConcurrentHashMap<>();
    private final Map<Audience, String> checks = new ConcurrentHashMap<>();

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
        return this.subscribe(recipient, null, null);
    }

    /**
     * Subscribes the recipient to reports of the given players only, replacing its previous
     * selection.
     */
    public boolean subscribe(final @NotNull Audience recipient, final @NotNull Collection<UUID> players) {
        return this.subscribe(recipient, players, null);
    }

    /**
     * Subscribes the recipient to reports of the given players and check, replacing its
     * previous selection.
     *
     * @param recipient the recipient
     * @param players   the players, or {@code null} for everyone
     * @param checkId   the check's config id, or {@code null} for every check
     * @return {@code true} when the recipient was not subscribed before
     */
    public boolean subscribe(
            final @NotNull Audience recipient,
            final @Nullable Collection<UUID> players,
            final @Nullable String checkId
    ) {
        if (players == null) {
            this.targets.remove(recipient);
        } else {
            this.targets.put(recipient, Set.copyOf(players));
        }
        if (checkId == null) {
            this.checks.remove(recipient);
        } else {
            this.checks.put(recipient, checkId);
        }
        return super.subscribe(recipient);
    }

    @Override
    public boolean remove(final @NotNull Audience recipient) {
        this.targets.remove(recipient);
        this.checks.remove(recipient);
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
        this.checks.clear();
        super.clear();
    }

    @Override
    public void publish(final @NotNull CheckReport report) {
        final Delivery delivery = this.delivery;

        for (final Audience recipient : this.recipients()) {
            final Set<UUID> players = this.targets.get(recipient);
            final String check = this.checks.get(recipient);

            if ((players == null || players.contains(report.player()))
                    && (check == null || check.equalsIgnoreCase(report.checkId()))) {
                delivery.deliver(recipient, report);
            }
        }
    }

    @FunctionalInterface
    public interface Delivery {

        void deliver(final @NotNull Audience recipient, final @NotNull CheckReport report);

    }

}
