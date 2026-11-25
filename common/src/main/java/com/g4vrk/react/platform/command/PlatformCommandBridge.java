package com.g4vrk.react.platform.command;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.key.CloudKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Platform adapter for Cloud command management, player selectors, and client tree synchronization.
 */
public interface PlatformCommandBridge {

    /**
     * Returns the Cloud command manager configured for {@link PlatformSender}.
     *
     * @return the command manager
     */
    @NotNull CommandManager<PlatformSender> manager();

    /**
     * Builds a platform-native or fallback single-player argument component.
     *
     * @param key      the Cloud argument key
     * @param required whether the argument is required
     * @return the component builder
     */
    CommandComponent.@NotNull Builder<PlatformSender, PlatformPlayer> singlePlayerArgument(
            final @NotNull CloudKey<PlatformPlayer> key,
            final boolean required
    );

    /**
     * Builds a platform-native or fallback multi-player selector argument component.
     *
     * @param key      the Cloud argument key
     * @param required whether the argument is required
     * @return the component builder
     */
    CommandComponent.@NotNull Builder<PlatformSender, Collection<PlatformPlayer>> multiplePlayersArgument(
            final @NotNull CloudKey<Collection<PlatformPlayer>> key,
            final boolean required
    );

    /**
     * Notifies the platform that commands under {@code rootLabel} changed after startup so
     * client-side command trees (such as Brigadier) can be refreshed.
     *
     * @param rootLabel the affected root command label
     */
    default void onCommandsChanged(
            final @NotNull String rootLabel
    ) {

    }

    /**
     * Executes a Cloud command tree mutation on the thread required by the platform dispatcher.
     *
     * @param mutation the mutation to execute
     */
    default void runTreeMutation(
            final @NotNull Runnable mutation
    ) {

        mutation.run();

    }

}
