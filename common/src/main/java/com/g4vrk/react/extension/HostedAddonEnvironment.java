package com.g4vrk.react.extension;

import com.g4vrk.react.api.addon.meta.AddonMetadata;
import com.g4vrk.react.api.extension.AddonEnvironment;
import com.g4vrk.react.api.player.TrackedPlayerRegistry;
import com.g4vrk.react.api.punish.Punishments;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * The environment of one addon.
 */
public final class HostedAddonEnvironment extends ScopedEnvironment implements AddonEnvironment {

    private final AddonMetadata metadata;
    private final TrackedPlayerRegistry players;
    private final Punishments punishments;

    public HostedAddonEnvironment(
            final @NotNull ExtensionContext context,
            final @NotNull ExtensionDescriptor descriptor,
            final @NotNull Path directory,
            final @NotNull ClassLoader classLoader
    ) {
        super(context, descriptor, directory, classLoader);
        this.metadata = descriptor.addonMetadata();
        this.players = context.players().view();
        this.punishments = context.punishments();
    }

    @Override
    public @NotNull AddonMetadata metadata() {
        return metadata;
    }

    @Override
    public @NotNull TrackedPlayerRegistry players() {
        return players;
    }

    @Override
    public @NotNull Punishments punishments() {
        return punishments;
    }

}
