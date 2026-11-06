package com.g4vrk.react.rcp.update;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * {@code .rcp/react-rcp.yml}: whether the React RCP is loaded and how it is updated.
 */
@ConfigSerializable
public record RCPSettings(Settings settings) {

    @ConfigSerializable
    public record Settings(boolean enabled, boolean autoUpdate, Channel channel) {
    }

    public enum Channel {

        /**
         * Only builds released with {@code [release]}.
         */
        RELEASE,

        /**
         * Every CI build, from the {@code nightly} prerelease.
         */
        ALL

    }

}
