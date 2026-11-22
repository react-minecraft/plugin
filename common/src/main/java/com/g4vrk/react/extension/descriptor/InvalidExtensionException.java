package com.g4vrk.react.extension.descriptor;

import org.jetbrains.annotations.NotNull;

/**
 * A jar in the addon or RCP folder that cannot be loaded: no metadata, a bad name or a
 * different api-version.
 */
public final class InvalidExtensionException extends Exception {

    public InvalidExtensionException(final @NotNull String message) {

        super(message);

    }

}
