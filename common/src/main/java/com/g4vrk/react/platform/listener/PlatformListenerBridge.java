package com.g4vrk.react.platform.listener;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Platform adapter for hooking core gameplay/packet listeners and scoped extension listeners.
 */
public interface PlatformListenerBridge {

    /**
     * Registers the platform listeners that feed React's core callbacks.
     *
     * @param callbacks the core engine callbacks
     */
    void registerCore(
            final @NotNull CoreListenerCallback callbacks
    );

    /**
     * Unregisters all core platform listeners.
     */
    void unregisterCore();

    /**
     * Creates a scoped platform listener container for a single extension.
     *
     * @param logger the extension logger
     * @return a new listener scope
     */
    @NotNull PlatformListenerScope createExtensionScope(
            final @NotNull Logger logger
    );

}
