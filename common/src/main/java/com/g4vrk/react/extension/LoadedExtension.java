package com.g4vrk.react.extension;

import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.event.addon.AddonDisableEvent;
import com.g4vrk.react.api.event.addon.AddonEnableEvent;
import com.g4vrk.react.api.rcp.RCPLifecycle;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.provider.RCPProvider;
import com.g4vrk.react.rcp.RCPHost;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One addon or RCP jar that is loaded: its class loader, environment and main object.
 *
 * <p>Every method runs on the extension thread of {@link ExtensionManager}.</p>
 */
public final class LoadedExtension {

    private final ExtensionContext context;
    private final ExtensionDescriptor descriptor;
    private final URLClassLoader classLoader;
    private final ScopedEnvironment environment;
    private final Object instance;

    private boolean enabled;

    private LoadedExtension(
            final @NotNull ExtensionContext context,
            final @NotNull ExtensionDescriptor descriptor,
            final @NotNull URLClassLoader classLoader,
            final @NotNull ScopedEnvironment environment,
            final @NotNull Object instance
    ) {
        this.context = context;
        this.descriptor = descriptor;
        this.classLoader = classLoader;
        this.environment = environment;
        this.instance = instance;
    }

    /**
     * Opens a jar, builds its environment and creates its main class. An addon also gets
     * {@link JavaAddon#onLoad()}.
     *
     * @param context    the plugin services
     * @param descriptor the metadata of the jar
     * @return the loaded, not yet enabled extension
     * @throws Exception when the jar cannot be loaded; nothing is left open then
     */
    static @NotNull LoadedExtension load(
            final @NotNull ExtensionContext context,
            final @NotNull ExtensionDescriptor descriptor
    ) throws Exception {
        final Path directory = descriptor.kind().data(context.dataFolder()).resolve(descriptor.name());
        Files.createDirectories(directory);

        final URLClassLoader classLoader = new URLClassLoader(
                context.logger().getName() + "#" + descriptor.name(),
                new URL[]{descriptor.jar().toUri().toURL()},
                RCPLifecycle.class.getClassLoader()
        );

        ScopedEnvironment environment = null;
        try {
            final Class<?> mainClass = Class.forName(descriptor.mainClass(), true, classLoader);
            final Object instance;

            if (descriptor.kind() == ExtensionKind.RCP) {
                final HostedRCPEnvironment rcp = new HostedRCPEnvironment(context, descriptor, directory, classLoader);
                environment = rcp;
                instance = createRCP(mainClass, rcp);
                context.rcpHost().attach(rcp);
            } else {
                final HostedAddonEnvironment addonEnvironment = new HostedAddonEnvironment(context, descriptor, directory, classLoader);
                environment = addonEnvironment;
                final JavaAddon addon = createAddon(mainClass);
                addon.init(addonEnvironment, classLoader);
                withContextLoader(classLoader, addon::onLoad);
                instance = addon;
            }

            environment.logger().info("Loaded {} '{}' v{}", descriptor.kind().displayName(), descriptor.name(), descriptor.version());
            return new LoadedExtension(context, descriptor, classLoader, environment, instance);
        } catch (final Exception | LinkageError failure) {
            if (environment != null) {
                release(context.rcpHost(), environment);
            }
            try {
                classLoader.close();
            } catch (final IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure instanceof Exception exception ? exception : new IllegalStateException(failure);
        }
    }

    private static @NotNull RCPLifecycle createRCP(
            final @NotNull Class<?> mainClass,
            final @NotNull RCPEnvironment environment
    ) throws Exception {
        if (!RCPLifecycle.class.isAssignableFrom(mainClass)) {
            throw new InvalidExtensionException(mainClass.getName() + " does not implement RCPLifecycle");
        }
        if (RCPProvider.class.isAssignableFrom(mainClass)) {
            return (RCPLifecycle) mainClass.getConstructor(RCPEnvironment.class).newInstance(environment);
        }
        return (RCPLifecycle) mainClass.getConstructor().newInstance();
    }

    private static @NotNull JavaAddon createAddon(final @NotNull Class<?> mainClass) throws Exception {
        if (!JavaAddon.class.isAssignableFrom(mainClass)) {
            throw new InvalidExtensionException(mainClass.getName() + " does not extend JavaAddon");
        }
        return (JavaAddon) mainClass.getConstructor().newInstance();
    }

    private static void release(final @NotNull RCPHost host, final @NotNull ScopedEnvironment environment) {
        if (environment instanceof HostedRCPEnvironment rcp) {
            host.detach(rcp);
        }
        environment.close();
    }

    /**
     * The metadata of the jar.
     *
     * @return the descriptor
     */
    public @NotNull ExtensionDescriptor descriptor() {
        return descriptor;
    }

    /**
     * The environment the extension got.
     *
     * @return the environment
     */
    public @NotNull ScopedEnvironment environment() {
        return environment;
    }

    /**
     * Whether the extension is enabled.
     *
     * @return the state
     */
    public boolean enabled() {
        return enabled;
    }

    void enable() throws Exception {
        if (enabled) {
            return;
        }
        if (instance instanceof JavaAddon addon) {
            withContextLoader(classLoader, () -> addon.enabled(true));
            context.events().post(new AddonEnableEvent(descriptor.name(), descriptor.version()));
        } else {
            withContextLoader(classLoader, ((RCPLifecycle) instance)::onEnable);
        }
        enabled = true;
    }

    void reload() throws Exception {
        if (enabled && instance instanceof RCPLifecycle lifecycle) {
            withContextLoader(classLoader, lifecycle::onReload);
        }
    }

    /**
     * Disables the extension, removes everything it registered and closes its class loader.
     * Failures are logged, the cleanup always runs.
     */
    void close() {
        try {
            if (enabled) {
                enabled = false;
                if (instance instanceof JavaAddon addon) {
                    withContextLoader(classLoader, () -> addon.enabled(false));
                    context.events().post(new AddonDisableEvent(descriptor.name(), descriptor.version()));
                } else {
                    withContextLoader(classLoader, ((RCPLifecycle) instance)::onDisable);
                }
            }
        } catch (final Throwable failure) {
            environment.logger().error("{} '{}' failed to disable", descriptor.kind().displayName(), descriptor.name(), failure);
        } finally {
            release(context.rcpHost(), environment);
            try {
                classLoader.close();
            } catch (final IOException failure) {
                environment.logger().warn("Could not close the class loader of {}", descriptor.name(), failure);
            }
        }
    }

    private static void withContextLoader(final @NotNull ClassLoader loader, final @NotNull Action action) throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            action.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @FunctionalInterface
    private interface Action {

        void run() throws Exception;

    }

    @Override
    public String toString() {
        return descriptor.kind().displayName() + " " + descriptor.name() + " v" + descriptor.version();
    }

}
