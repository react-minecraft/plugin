package com.g4vrk.react.api.addon;

import com.g4vrk.react.api.addon.meta.AddonMetadata;
import com.g4vrk.react.api.extension.AddonEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Base class of every addon.
 *
 * <p>The main class named in {@code react-addon.yml} extends this class and has a public
 * constructor without parameters. The plugin sets the environment before {@link #onLoad()}.</p>
 */
@ApiStatus.AvailableSince("2.0")
public abstract class JavaAddon implements Addon {

    private AddonEnvironment environment;
    private ClassLoader classLoader;
    private boolean enabled;

    /**
     * Creates the addon. The environment is not set yet: use it from {@link #onLoad()} on.
     */
    protected JavaAddon() {
    }

    /**
     * Hands the addon its environment. Called once by the plugin.
     *
     * @param environment the environment
     * @param classLoader the loader of the addon jar
     */
    @ApiStatus.Internal
    public final void init(final @NotNull AddonEnvironment environment, final @NotNull ClassLoader classLoader) {
        if (this.environment != null) {
            throw new IllegalStateException("Addon " + environment.name() + " is already initialized");
        }
        this.environment = Objects.requireNonNull(environment, "environment");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    /**
     * Enables or disables the addon, calling {@link #onEnable()} or {@link #onDisable()}
     * when the state changes.
     *
     * @param enabled the new state
     */
    @ApiStatus.Internal
    public final void enabled(final boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    @Override
    public final @NotNull AddonEnvironment environment() {
        return Objects.requireNonNull(environment, "The environment is set before onLoad");
    }

    @Override
    public final @NotNull Path directory() {
        return environment().directory();
    }

    @Override
    public final @NotNull String name() {
        return environment().name();
    }

    @Override
    public final @NotNull AddonMetadata metadata() {
        return environment().metadata();
    }

    @Override
    public final @NotNull Logger logger() {
        return environment().logger();
    }

    @Override
    public final boolean enabled() {
        return enabled;
    }

    /**
     * The loader of the addon jar.
     *
     * @return the class loader
     */
    public final @NotNull ClassLoader classLoader() {
        return classLoader;
    }

    @Override
    public @Nullable InputStream getResource(final @NotNull String fileName) {
        try {
            final URL url = classLoader.getResource(fileName);
            if (url == null) {
                return null;
            }
            final URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            return connection.getInputStream();
        } catch (final IOException ex) {
            return null;
        }
    }

    @Override
    public void saveResource(final @NotNull String fileName, final boolean replace) {
        final String resource = fileName.replace(File.separatorChar, '/');
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("The resource name is empty");
        }

        final Path target = directory().resolve(resource).normalize();
        if (!target.startsWith(directory())) {
            throw new IllegalArgumentException("The resource " + resource + " is outside the addon folder");
        }
        if (Files.exists(target) && !replace) {
            return;
        }

        try (InputStream in = getResource(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("The addon jar has no resource " + resource);
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException ex) {
            logger().error("Could not save {} to {}", resource, target, ex);
        }
    }

}
