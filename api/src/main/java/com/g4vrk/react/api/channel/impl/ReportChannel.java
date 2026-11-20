package com.g4vrk.react.api.channel.impl;

import com.g4vrk.react.api.channel.CheckReport;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
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

    private final Map<Audience, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final Set<Audience> recipients = Collections.unmodifiableSet(subscriptions.keySet());

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

        final Subscription subscription = new Subscription(players == null ? null : Set.copyOf(players), checkId);
        return subscriptions.put(recipient, subscription) == null;

    }

    @Override
    public boolean remove(final @NotNull Audience recipient) {

        return subscriptions.remove(recipient) != null;

    }

    /**
     * Removes any subscription or subscribes to reports of every player.
     *
     * @return whether the recipient is subscribed after the change
     */
    public boolean toggle(final @NotNull Audience recipient) {

        return subscriptions.compute(recipient, (ignored, current) -> current == null ? new Subscription(null, null) : null) != null;

    }

    /**
     * Removes a matching subscription or replaces it with the requested filter.
     *
     * @return whether the recipient is subscribed after the change
     */
    public boolean toggle(
            final @NotNull Audience recipient,
            final @Nullable Set<UUID> players,
            final @Nullable String checkId
    ) {

        final Subscription requested = new Subscription(players == null ? null : Set.copyOf(players), checkId);
        return subscriptions.compute(recipient, (ignored, current) ->
                current != null && current.matches(requested.players(), requested.checkId()) ? null : requested) != null;

    }

    @Override
    public @NotNull Set<Audience> recipients() {

        return recipients;

    }

    /**
     * The players the recipient follows, or {@code null} when it follows everyone.
     */
    public @Nullable Set<UUID> targets(final @NotNull Audience recipient) {

        final Subscription subscription = subscriptions.get(recipient);
        return subscription == null ? null : subscription.players();

    }

    /**
     * Checks whether the recipient already has this exact report filter.
     */
    public boolean follows(
            final @NotNull Audience recipient,
            final @Nullable Set<UUID> players,
            final @Nullable String checkId
    ) {

        final Subscription subscription = subscriptions.get(recipient);
        return subscription != null && subscription.matches(players, checkId);

    }

    @Override
    public void clear() {

        subscriptions.clear();

    }

    @Override
    public void publish(final @NotNull CheckReport report) {
        final Delivery delivery = this.delivery;

        for (final Map.Entry<Audience, Subscription> entry : subscriptions.entrySet()) {
            final Set<UUID> players = entry.getValue().players();
            final String check = entry.getValue().checkId();

            if ((players == null || players.contains(report.player()))
                    && (check == null || check.equalsIgnoreCase(report.checkId()))) {
                delivery.deliver(entry.getKey(), report);
            }
        }
    }

    /**
     * The complete report filter published by one subscription change.
     */
    private record Subscription(@Nullable Set<UUID> players, @Nullable String checkId) {

        private boolean matches(final @Nullable Set<UUID> targets, final @Nullable String check) {

            return Objects.equals(players, targets) && (checkId == null ? check == null : checkId.equalsIgnoreCase(check));

        }

    }

    @FunctionalInterface
    public interface Delivery {

        void deliver(final @NotNull Audience recipient, final @NotNull CheckReport report);

    }

}
