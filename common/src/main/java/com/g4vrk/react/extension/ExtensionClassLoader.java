package com.g4vrk.react.extension;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads an addon or RCP jar: classes come from the plugin first, resources from the jar first.
 *
 * <p>Classes stay parent-first so the API types are shared with the plugin. Resources do not:
 * the plugin ships {@code en/actions.yml} itself, and a parent-first lookup handed an extension
 * the plugin's file instead of its own default.</p>
 */
final class ExtensionClassLoader extends URLClassLoader {

    private Map<String, ExtensionClassLoader> imports = Map.of();

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

    void addLibrary(final URL library) {

        addURL(library);

    }

    void link(final Iterable<LoadedExtension> dependencies) {

        final Map<String, ExtensionClassLoader> packages = new LinkedHashMap<>();
        for (final LoadedExtension dependency : dependencies) {
            for (final String name : dependency.plan().exports()) {
                if (packages.putIfAbsent(name, dependency.loader()) != null) {
                    throw new IllegalArgumentException("Ambiguous imported package: " + name);
                }
            }
        }
        imports = Map.copyOf(packages);

    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {

        final int separator = name.lastIndexOf('.');
        final ExtensionClassLoader provider = separator < 0 ? null : imports.get(name.substring(0, separator));
        if (provider == null) {
            return super.findClass(name);
        }
        final Class<?> type = provider.loadClass(name);
        if (type.getClassLoader() != provider) {
            throw new ClassNotFoundException(name + " is not defined by its exporting extension");
        }
        return type;

    }

}
