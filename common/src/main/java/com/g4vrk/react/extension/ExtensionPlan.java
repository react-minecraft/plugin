package com.g4vrk.react.extension;

import com.g4vrk.react.api.extension.ExtensionId;
import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrap;
import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrapContext;
import com.g4vrk.react.extension.library.LibraryCache;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Reads and validates the bootstrap declarations of one extension JAR.
 */
final class ExtensionPlan implements ExtensionBootstrapContext {

    static final String BOOTSTRAP_SERVICE = "META-INF/services/" + ExtensionBootstrap.class.getName();

    private final Map<ExtensionId, Dependency> dependencies = new LinkedHashMap<>();
    private final Set<String> exports = new LinkedHashSet<>();
    private final Map<URI, String> libraries = new LinkedHashMap<>();
    private final String namespace;
    private ExtensionId id;
    private int apiVersion = 1;
    private boolean declared;
    private boolean sealed;

    private ExtensionPlan(final ExtensionDescriptor descriptor) {

        namespace = descriptor.kind().name().toLowerCase(Locale.ROOT);
        id = new ExtensionId(namespace, descriptor.name().toLowerCase(Locale.ROOT));

    }

    static ExtensionPlan read(final ExtensionDescriptor descriptor) throws IOException {

        final ExtensionPlan plan = new ExtensionPlan(descriptor);
        try (URLClassLoader loader = new ExtensionClassLoader(
                descriptor.name() + " bootstrap", descriptor.jar().toUri().toURL(), ExtensionBootstrap.class.getClassLoader()
        )) {
            final var providers = ServiceLoader.load(ExtensionBootstrap.class, loader).stream()
                    .filter(provider -> provider.type().getClassLoader() == loader).toList();
            if (providers.size() > 1) {
                throw new IllegalArgumentException("Multiple bootstraps for " + descriptor.name());
            }
            if (!providers.isEmpty()) {
                providers.get(0).get().bootstrap(plan);
            }
        } finally {
            plan.sealed = true;
        }
        if (plan.dependencies.containsKey(plan.id)) {
            throw new IllegalArgumentException("An extension cannot depend on itself: " + plan.id);
        }
        return plan;

    }

    ExtensionId id() {

        return id;

    }

    int apiVersion() {

        return apiVersion;

    }

    Map<ExtensionId, Dependency> dependencies() {

        return Collections.unmodifiableMap(dependencies);

    }

    Set<String> exports() {

        return Set.copyOf(exports);

    }

    void loadLibraries(final ExtensionClassLoader loader, final Path directory) throws IOException, InterruptedException {

        final LibraryCache cache = new LibraryCache();
        for (final Map.Entry<URI, String> library : libraries.entrySet()) {
            loader.addLibrary(cache.resolve(directory.resolve(".libraries"), library.getKey(), library.getValue()).toUri().toURL());
        }

    }

    @Override
    public void identify(final @NotNull ExtensionId id, final int apiVersion) {

        checkOpen();
        if (declared || !namespace.equals(id.namespace()) || apiVersion < 1) {
            throw new IllegalArgumentException("Invalid or duplicate identity: " + id);
        }
        declared = true;
        this.id = id;
        this.apiVersion = apiVersion;

    }

    @Override
    public void exportPackage(final @NotNull String packageName) {

        checkOpen();
        if (!packageName.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+")) {
            throw new IllegalArgumentException("Invalid package: " + packageName);
        }
        exports.add(packageName);

    }

    @Override
    public void require(final @NotNull ExtensionId id, final int apiVersion) {

        addDependency(id, apiVersion, true);

    }

    @Override
    public void optional(final @NotNull ExtensionId id, final int apiVersion) {

        addDependency(id, apiVersion, false);

    }

    @Override
    public void library(final @NotNull URI source, final @NotNull String sha256) {

        checkOpen();
        final String previous = libraries.putIfAbsent(source, sha256);
        if (previous != null && !previous.equals(sha256)) {
            throw new IllegalArgumentException("Conflicting library digests: " + source.getPath());
        }

    }

    private void addDependency(final ExtensionId id, final int apiVersion, final boolean required) {

        checkOpen();
        if (apiVersion < 1 || dependencies.putIfAbsent(id, new Dependency(apiVersion, required)) != null) {
            throw new IllegalArgumentException("Invalid or duplicate dependency: " + id);
        }

    }

    private void checkOpen() {

        if (sealed) {
            throw new IllegalStateException("Bootstrap has finished");
        }

    }

    /**
     * A link to a specific version of another extension's API.
     */
    record Dependency(int apiVersion, boolean required) {
    }

}
