package com.g4vrk.react.api.extension.bootstrap;

import com.g4vrk.react.api.extension.ExtensionId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Collects declarations for one extension. Valid only during bootstrap.
 */
@ApiStatus.NonExtendable
public interface ExtensionBootstrapContext {

    /** Declares a stable ID and a positive version of the extension's exported API. */
    void identify(final @NotNull ExtensionId id, final int apiVersion);

    /** Exports exactly this package to direct dependents. */
    void exportPackage(final @NotNull String packageName);

    /** Requires an installed, enabled extension with the specified API version. */
    void require(final @NotNull ExtensionId id, final int apiVersion);

    /** Links a compatible extension when it is available. */
    void optional(final @NotNull ExtensionId id, final int apiVersion);

    /** Adds a pinned HTTPS JAR to this extension's private classpath. */
    void library(final @NotNull URI source, final @NotNull String sha256);

}
