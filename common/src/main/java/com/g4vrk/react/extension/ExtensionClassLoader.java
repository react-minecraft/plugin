package com.g4vrk.react.extension;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Loads an addon or RCP jar: classes come from the plugin first, resources from the jar first.
 *
 * <p>Classes stay parent-first so the API types are shared with the plugin. Resources do not:
 * the plugin ships {@code en/actions.yml} itself, and a parent-first lookup handed an extension
 * the plugin's file instead of its own default.</p>
 */
final class ExtensionClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    ExtensionClassLoader(
            final @NotNull String name,
            final @NotNull URL jar,
            final @NotNull ClassLoader parent
    ) {
        super(name, new URL[]{jar}, parent);
    }

    @Override
    public @Nullable URL getResource(final @NotNull String name) {
        final URL own = findResource(name);
        return own != null ? own : super.getResource(name);
    }

}
