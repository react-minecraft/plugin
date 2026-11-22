package com.g4vrk.react.api.addon;

import com.g4vrk.react.api.addon.meta.AddonMetadata;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
     * Prepares this addon for an extension reboot before its resources are removed.
     *
     * <p>The host invokes this on its extension thread and keeps the environment usable until
     * the future completes, up to 15 seconds. A failure or timeout cancels the reboot and leaves
     * the addon enabled; {@link #cancelDisablePreparation()} is then called.
     * This hook is not called during shutdown or direct disabling of React.</p>
     *
     * @return completion of the preparation
     */
    public @NotNull CompletableFuture<Void> prepareDisable() {

        return CompletableFuture.completedFuture(null);

    }

    /**
     * Reverses preparation after an extension reboot was cancelled. Called on the extension thread
     * for every addon whose preparation started, including one that already completed it.
     */
    public void cancelDisablePreparation() {

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

        final Path base = directory().toAbsolutePath().normalize();
        final Path target = base.resolve(resource).normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            throw new IllegalArgumentException("The resource " + resource + " is outside the addon folder");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !replace) {
            return;
        }

        try (InputStream in = getResource(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("The addon jar has no resource " + resource);
            }
            Path parent = base;
            for (final Path part : base.relativize(target.getParent())) {
                parent = parent.resolve(part);
                if (Files.isSymbolicLink(parent)) {
                    throw new IOException("Resource folder is a symbolic link: " + parent);
                }
                if (Files.notExists(parent, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectory(parent);
                }
            }
            try (OutputStream out = Files.newOutputStream(
                    target,
                    replace ? StandardOpenOption.CREATE : StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                in.transferTo(out);
            }
        } catch (final IOException ex) {
            logger().error("Could not save {} to {}", resource, target, ex);
        }

    }

}
