package com.g4vrk.react.update;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.Permissions;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.channel.ReactChannels;
import com.g4vrk.react.api.event.EventBus;
import com.g4vrk.react.api.event.plugin.UpdateDownloadedEvent;
import com.g4vrk.react.config.model.ActionsConfig;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tells the server about downloaded updates: posts {@link UpdateDownloadedEvent}, messages the
 * alert recipients and greets players with {@code react.notify.update} on join.
 */
public final class UpdateNotifier implements Consumer<UpdateDownloadedEvent>, ReloadObserver<ActionsConfig> {

    private final EventBus events;
    private final ActionParser<Audience> parser;
    private final BooleanSupplier notify;
    private final Supplier<Iterable<UpdateDownloadedEvent>> pending;

    private volatile ExecutableActionList<? super Audience> plugin;
    private volatile ExecutableActionList<? super Audience> extension;

    public UpdateNotifier(
            final @NotNull EventBus events,
            final @NotNull ActionParser<Audience> parser,
            final @NotNull ActionsConfig config,
            final @NotNull BooleanSupplier notify,
            final @NotNull Supplier<Iterable<UpdateDownloadedEvent>> pending
    ) {
        this.events = events;
        this.parser = parser;
        this.notify = notify;
        this.pending = pending;
        this.onReload(config);
    }

    @Override
    public void onReload(final @NotNull ActionsConfig config) {
        this.plugin = parser.parseAll(config.updates().plugin());
        this.extension = parser.parseAll(config.updates().extension());
    }

    @Override
    public void accept(final @NotNull UpdateDownloadedEvent event) {
        events.post(event);
        if (!notify.getAsBoolean()) {
            return;
        }
        for (final Audience recipient : ReactChannels.ALERTS.recipients()) {
            send(recipient, event);
        }
    }

    /**
     * Tells a joining player about every update that is not applied yet.
     *
     * @param player the player
     */
    public void join(final @NotNull Player player) {
        if (!notify.getAsBoolean() || !player.hasPermission(Permissions.NOTIFY_UPDATE)) {
            return;
        }
        for (final UpdateDownloadedEvent event : pending.get()) {
            send(player, event);
        }
    }

    private void send(final @NotNull Audience audience, final @NotNull UpdateDownloadedEvent event) {
        final ExecutableActionList<? super Audience> actions =
                event.kind() == UpdateDownloadedEvent.Kind.PLUGIN ? plugin : extension;
        actions.run(audience, text -> text
                .replace("<name>", event.name())
                .replace("<version>", event.version()));
    }

}
