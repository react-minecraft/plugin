package com.g4vrk.react.api.extension;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Stable identifier of an installed React extension.
 */
public record ExtensionId(@NotNull String namespace, @NotNull String name) {

    private static final Pattern PART = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

    public ExtensionId {

        if (!PART.matcher(namespace).matches() || !PART.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid extension ID: " + namespace + ':' + name);
        }

    }

    public static @NotNull ExtensionId rcp(final @NotNull String name) {

        return new ExtensionId("rcp", name);

    }

    public static @NotNull ExtensionId addon(final @NotNull String name) {

        return new ExtensionId("addon", name);

    }

    @Override
    public @NotNull String toString() {

        return namespace + ':' + name;

    }

}
