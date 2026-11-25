package com.g4vrk.react.extension.library;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Starts React inside an isolated class loader populated from SHA-256 verified runtime library
 * lockfiles declared by the build.
 */
public final class RuntimeLibraryLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    private static final String CORE_PACKAGE = "com.g4vrk.react.";
    private static final String LOADER_PACKAGE = CORE_PACKAGE + "extension.library.";
    private final ClassLoader ownedPlatformLoader;
    private final Set<String> platformPackages;
    private final Path directory;
    private final LibraryCache cache = new LibraryCache();
    private final Set<String> loaded = new HashSet<>();

    private RuntimeLibraryLoader(
            final @NotNull URL codeSource,
            final @NotNull ClassLoader parent,
            final @NotNull Path directory,
            final ClassLoader externalPlatformLoader,
            final @NotNull Set<String> platformPackages,
            final @NotNull Set<String> isolatedPackages
    ) {

        super("React runtime", new URL[]{codeSource}, parent);
        this.directory = directory;
        this.ownedPlatformLoader = new PlatformClassLoader(
                this,
                parent,
                externalPlatformLoader,
                Set.of("com.github.retrooper.packetevents.", "io.github.retrooper.packetevents."),
                isolatedPackages
        );
        this.platformPackages = Set.copyOf(platformPackages);

    }

    @Override
    public void close() throws IOException {

        super.close();

    }

    private static final class PlatformClassLoader extends ClassLoader {

        private static final String RESOURCE_ROOT = "META-INF/react-platform/";

        private final RuntimeLibraryLoader runtimeLibraryLoader;
        private final ClassLoader externalPlatformLoader;
        private final ClassLoader platformResourceLoader;
        private final Set<String> externalPackages;
        private final Set<String> isolatedPackages;

        private PlatformClassLoader(
                final RuntimeLibraryLoader runtimeLibraryLoader,
                final ClassLoader parent,
                final ClassLoader externalPlatformLoader,
                final Set<String> externalPackages,
                final Set<String> isolatedPackages
        ) {

            super(parent);
            this.runtimeLibraryLoader = runtimeLibraryLoader;
            this.externalPlatformLoader = externalPlatformLoader;
            this.platformResourceLoader = parent;
            this.externalPackages = Set.copyOf(externalPackages);
            this.isolatedPackages = Set.copyOf(isolatedPackages);

        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {

            if (externalPlatformLoader != null && matchesPackage(externalPackages, name)) {
                try {
                    return externalPlatformLoader.loadClass(name);
                } catch (final ClassNotFoundException unavailable) {
                    /*
                     * Use the bundled platform library when the installed provider lacks this class.
                     */
                }
            }
            if (matchesPackage(isolatedPackages, name)) {
                synchronized (getClassLoadingLock(name)) {
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
            return super.loadClass(name, resolve);

        }

        @Override
        public URL getResource(final String name) {

            final URL parentResource = platformResourceLoader.getResource(name);
            return parentResource == null ? findResource(name) : parentResource;

        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {

            final String resourceName = RESOURCE_ROOT + name.replace('.', '/') + ".class";
            final URL resource = platformResourceLoader.getResource(resourceName);
            if (resource == null) {
                return runtimeLibraryLoader.findRuntimeLibraryClass(name);
            }
            try (InputStream input = resource.openStream()) {
                final byte[] bytecode = input.readAllBytes();
                return defineClass(name, bytecode, 0, bytecode.length);
            } catch (final IOException failure) {
                throw new ClassNotFoundException(name, failure);
            }

        }

        @Override
        protected URL findResource(final String name) {

            return platformResourceLoader.getResource(RESOURCE_ROOT + name);

        }

        @Override
        protected Enumeration<URL> findResources(final String name) {

            final URL resource = findResource(name);
            return resource == null
                    ? Collections.emptyEnumeration()
                    : Collections.enumeration(Set.of(resource));

        }

    }

    /**
     * Loads the {@code core} library group and invokes {@code entryPoint#initialize(initParamType)}.
     *
     * @param codeSource    location of the hosting plugin/mod JAR
     * @param parent        parent class loader provided by the server platform
     * @param dataDirectory React's data directory (libraries are cached in {@code libraries})
     * @param warningLogger sink for non-fatal close warnings
     * @param entryPoint    fully qualified runtime bootstrap class name
     * @param initParamType parameter type of {@code initialize(...)}
     * @param initArg       argument passed to {@code initialize(...)}
     * @return a two-element lifecycle array {@code [onEnable, onDisable]}
     */
    public static @NotNull Runnable[] start(
            final @NotNull URL codeSource,
            final @NotNull ClassLoader parent,
            final @NotNull Path dataDirectory,
            final @NotNull Consumer<String> warningLogger,
            final @NotNull String entryPoint,
            final @NotNull Class<?> initParamType,
            final @NotNull Object initArg,
            final ClassLoader platformLoader,
            final @NotNull Set<String> platformPackages,
            final @NotNull Set<String> isolatedPackages
    ) {

        final RuntimeLibraryLoader loader = new RuntimeLibraryLoader(
                codeSource,
                parent,
                dataDirectory,
                platformLoader,
                platformPackages,
                isolatedPackages
        );
        try {
            loader.loadGroup("core");
            final Class<?> entry = Class.forName(entryPoint, true, loader);
            final Runnable[] lifecycle = (Runnable[]) entry.getMethod("initialize", initParamType).invoke(null, initArg);
            return new Runnable[]{lifecycle[0], () -> {
                try {
                    lifecycle[1].run();
                } finally {
                    try {
                        loader.close();
                    } catch (final IOException failure) {
                        warningLogger.accept("Could not close React runtime: " + failure.getMessage());
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

    public synchronized void loadGroup(
            final @NotNull String group
    ) throws IOException, InterruptedException {

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

    private Class<?> findRuntimeLibraryClass(
            final @NotNull String name
    ) throws ClassNotFoundException {

        synchronized (getClassLoadingLock(name)) {
            final Class<?> type = findLoadedClass(name);
            return type == null ? findClass(name) : type;
        }

    }

    @Override
    protected Class<?> loadClass(
            final String name,
            final boolean resolve
    ) throws ClassNotFoundException {

        if (matchesPackage(platformPackages, name)) {
            return ownedPlatformLoader.loadClass(name);
        }
        if (!name.startsWith(CORE_PACKAGE) || name.startsWith(LOADER_PACKAGE)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
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

    private static boolean matchesPackage(
            final Set<String> packages,
            final String name
    ) {

        for (final String packageName : packages) {
            if (name.startsWith(packageName)) {
                return true;
            }
        }
        return false;

    }

}
