package com.g4vrk.react.update;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * {@code update/plugin.yml}, {@code update/rcp/<name>.yml} or {@code update/addons/<name>.yml}.
 *
 * @param enabled       whether the component is updated at all
 * @param channel       the releases it follows
 * @param pinnedVersion a release to stay on, empty to follow the channel
 */
@ConfigSerializable
public record ComponentSettings(boolean enabled, UpdateChannel channel, String pinnedVersion) {

    /**
     * The channel, {@link UpdateChannel#RELEASE} when the file has none.
     *
     * @return the channel
     */
    public @NotNull UpdateChannel effectiveChannel() {
        return channel == null ? UpdateChannel.RELEASE : channel;
    }

    /**
     * The pinned version.
     *
     * @return the version, or {@code null} when none is pinned
     */
    public @Nullable String pinned() {
        return pinnedVersion == null || pinnedVersion.isBlank() ? null : pinnedVersion.strip();
    }

}
