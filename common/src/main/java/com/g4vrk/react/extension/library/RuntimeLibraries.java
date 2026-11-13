package com.g4vrk.react.extension.library;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Starts React with the runtime classpath declared by its build.
 */
public final class RuntimeLibraries extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    private static final String CORE_PACKAGE = "com.g4vrk.react.";
    private static final String LOADER_PACKAGE = CORE_PACKAGE + "extension.library.";
    private final Path directory;
    private final LibraryCache cache = new LibraryCache();
    private final Set<String> loaded = new HashSet<>();

    private RuntimeLibraries(final JavaPlugin plugin) {

        super("React runtime", new URL[]{plugin.getClass().getProtectionDomain().getCodeSource().getLocation()}, plugin.getClass().getClassLoader());
        directory = plugin.getDataFolder().toPath().resolve(".libraries");

    }

    public static Runnable[] start(final JavaPlugin plugin, final String entryPoint) {

        final RuntimeLibraries loader = new RuntimeLibraries(plugin);
        try {
            loader.loadGroup("core");
            final Class<?> entry = Class.forName(entryPoint, true, loader);
            final Runnable[] lifecycle = (Runnable[]) entry.getMethod("initialize", JavaPlugin.class).invoke(null, plugin);
            return new Runnable[]{lifecycle[0], () -> {
                try {
                    lifecycle[1].run();
                } finally {
                    try {
                        loader.close();
                    } catch (final IOException failure) {
                        plugin.getLogger().warning("Could not close React runtime: " + failure.getMessage());
                    }
                }
            }};
        } catch (final Exception | LinkageError failure) {
            try {
                loader.close();
            } catch (final IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not start the React runtime", failure);
        }

    }

    public synchronized void loadGroup(final String group) throws IOException, InterruptedException {

        if (loaded.contains(group)) {
            return;
        }
        final String resource = "META-INF/react-libraries/" + group + ".lock";
        final URL index = findResource(resource);
        if (index == null) {
            throw new IOException("Missing React runtime library group " + group);
        }
        final Set<URL> resolved = new LinkedHashSet<>();
        try (InputStream input = index.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (final String line : reader.lines().toList()) {
                final String[] columns = line.split(" ", 2);
                if (columns.length != 2) {
                    throw new IOException("Invalid library declaration in " + resource);
                }
                resolved.add(cache.resolve(directory, URI.create(columns[1]), columns[0]).toUri().toURL());
            }
        }
        resolved.forEach(this::addURL);
        loaded.add(group);

    }

    @Override
    protected synchronized Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {

        if (!name.startsWith(CORE_PACKAGE) || name.startsWith(LOADER_PACKAGE)) {
            return super.loadClass(name, resolve);
        }
        Class<?> type = findLoadedClass(name);
        if (type == null) {
            type = findClass(name);
        }
        if (resolve) {
            resolveClass(type);
        }
        return type;

    }

}
