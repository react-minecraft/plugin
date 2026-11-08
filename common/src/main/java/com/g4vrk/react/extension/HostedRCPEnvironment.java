package com.g4vrk.react.extension;

import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.event.RCPPlayerListener;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.registry.RCPPlayerRegistry;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.schedula.task.TickSchedule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The environment of one RCP: verdicts go to the {@link AimAI} check of the player, player
 * listeners get the events of {@link RCPHost}.
 */
public final class HostedRCPEnvironment extends ScopedEnvironment implements RCPEnvironment {

    private final RCPHost host;
    private final RCPMetadata metadata;
    private final List<RCPPlayerListener> playerListeners = new CopyOnWriteArrayList<>();

    private volatile @Nullable String status;
    private volatile boolean closed;

    public HostedRCPEnvironment(
            final @NotNull ExtensionContext context,
            final @NotNull ExtensionDescriptor descriptor,
            final @NotNull Path directory,
            final @NotNull ClassLoader classLoader
    ) {
        super(context, descriptor, directory, classLoader);
        this.host = context.rcpHost();
        this.metadata = descriptor.rcpMetadata();
    }

    @Override
    public @NotNull RCPMetadata metadata() {
        return metadata;
    }

    @Override
    public @NotNull RCPPlayerRegistry players() {
        return host;
    }

    @Override
    public void fire(final @NotNull RCPPlayer player, final @NotNull InferenceResult result) {
        Objects.requireNonNull(result, "result");
        if (closed || !(player instanceof TrackedPlayerImpl reactPlayer)) {
            return;
        }
        scheduler().scheduleEntity(reactPlayer.bukkitPlayer, () -> {
            final AimAI check = reactPlayer.checkManager.getCheck(AimAI.class);
            if (check != null) {
                check.onResult(result);
            }
        }, TickSchedule.instant());
    }

    @Override
    public void listen(final @NotNull RCPPlayerListener listener) {
        if (closed) {
            throw new IllegalStateException("RCP '" + name() + "' is disabled");
        }
        playerListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void status(final @Nullable String status) {
        this.status = status;
    }

    /**
     * The status line the RCP set last.
     *
     * @return the status, or {@code null} once the RCP is disabled or when it set none
     */
    public @Nullable String currentStatus() {
        return closed ? null : status;
    }

    /**
     * The player listeners the RCP added.
     *
     * @return a live view of the listeners
     */
    public @NotNull List<RCPPlayerListener> playerListeners() {
        return playerListeners;
    }

    @Override
    public void close() {
        closed = true;
        playerListeners.clear();
        status = null;
        super.close();
    }

}
