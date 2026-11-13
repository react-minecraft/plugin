package com.g4vrk.react.api.extension.bootstrap;

import org.jetbrains.annotations.NotNull;

/**
 * Declares an addon's or RCP's dependencies before React loads its main class.
 * Register one public no-argument implementation through Java's service provider mechanism.
 */
@FunctionalInterface
public interface ExtensionBootstrap {

    /**
     * Declares the extension's configuration. Dependency and library classes are available
     * after this method returns and React resolves the declarations.
     */
    void bootstrap(final @NotNull ExtensionBootstrapContext context);

}
