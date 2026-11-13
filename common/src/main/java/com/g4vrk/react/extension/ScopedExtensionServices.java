package com.g4vrk.react.extension;

import com.g4vrk.react.api.extension.ExtensionId;
import com.g4vrk.react.api.extension.ExtensionServices;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes services only for the lifetime of an enabled extension.
 */
final class ScopedExtensionServices implements ExtensionServices {

    private final ExtensionPlan plan;
    private final ClassLoader loader;
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private volatile Map<ExtensionId, LoadedExtension> dependencies;
    private volatile boolean enabled;
    private volatile Thread publisher;

    ScopedExtensionServices(final ExtensionPlan plan, final ClassLoader loader, final Map<ExtensionId, LoadedExtension> dependencies) {

        this.plan = plan;
        this.loader = loader;
        this.dependencies = Map.copyOf(dependencies);

    }

    void begin() {

        publisher = Thread.currentThread();

    }

    void publish() {

        publisher = null;
        enabled = true;

    }

    void close() {

        enabled = false;
        publisher = null;
        services.clear();
        dependencies = Map.of();

    }

    boolean available(final ExtensionId id) {

        final LoadedExtension dependency = dependencies.get(id);
        return dependency != null && dependency.services().enabled;

    }

    Set<ExtensionId> dependencyIds() {

        return dependencies.keySet();

    }

    @Override
    public <T> void register(final Class<T> type, final T service) {

        if (publisher != Thread.currentThread()) {
            throw new IllegalStateException("Services can only be registered during onEnable");
        }
        if (!type.isInterface() || type.getClassLoader() != loader || !plan.exports().contains(type.getPackageName())) {
            throw new IllegalArgumentException("Service must be an exported interface: " + type.getName());
        }
        if (services.putIfAbsent(type, type.cast(service)) != null) {
            throw new IllegalArgumentException("Service already registered: " + type.getName());
        }

    }

    @Override
    public <T> T find(final ExtensionId provider, final Class<T> type) {

        final LoadedExtension dependency = dependencies.get(provider);
        if (dependency == null || !dependency.services().enabled) {
            return null;
        }
        return type.cast(dependency.services().services.get(type));

    }

}
