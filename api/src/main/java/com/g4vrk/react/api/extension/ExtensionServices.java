package com.g4vrk.react.api.extension;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Services scoped to the current activation of an addon or RCP.
 */
@ApiStatus.NonExtendable
public interface ExtensionServices {

    /** Publishes one implementation per exported interface during enable. */
    <T> void register(final @NotNull Class<T> type, final @NotNull T service);

    /** Finds a service supplied by a directly linked dependency, if available. */
    <T> @Nullable T find(final @NotNull ExtensionId provider, final @NotNull Class<T> type);

    /** Finds a service or throws if the provider did not publish it. */
    default <T> @NotNull T require(final @NotNull ExtensionId provider, final @NotNull Class<T> type) {

        final T service = find(provider, type);
        if (service == null) {
            throw new IllegalStateException("Missing service " + type.getName() + " from " + provider);
        }
        return service;

    }

}
