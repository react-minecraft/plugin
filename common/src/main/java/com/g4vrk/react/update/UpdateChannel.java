package com.g4vrk.react.update;

/**
 * Which GitHub releases a component follows.
 */
public enum UpdateChannel {

    /**
     * The latest release made with {@code [release]}.
     */
    RELEASE,

    /**
     * The {@code nightly} prerelease, rebuilt on every push.
     */
    PRERELEASE

}
