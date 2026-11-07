package com.g4vrk.react.api.extension;

import com.g4vrk.react.api.addon.meta.AddonMetadata;
import com.g4vrk.react.api.player.TrackedPlayerRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * What the plugin gives one addon: everything an RCP gets and the tracked players.
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0")
public interface AddonEnvironment extends ExtensionEnvironment {

    /**
     * The addon's {@code react-addon.yml}.
     *
     * @return the metadata
     */
    @NotNull AddonMetadata metadata();

    /**
     * Online players the plugin tracks.
     *
     * @return the player registry
     */
    @NotNull TrackedPlayerRegistry players();

}
