package com.g4vrk.react.extension.scope;

import com.g4vrk.react.extension.descriptor.ExtensionDescriptor;
import com.g4vrk.react.extension.environment.ExtensionContext;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.config.ConfigFile;
import com.g4vrk.react.api.extension.ConfigFactory;
import com.g4vrk.react.api.extension.ExtensionEnvironment;
import com.g4vrk.react.api.extension.ExtensionId;
import com.g4vrk.react.api.extension.ExtensionService;
import com.g4vrk.react.api.platform.PlatformServer;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.text.TextFormatter;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * What every addon and RCP gets. Everything registered through it is removed by
 * {@link #close()}.
 */
public abstract class ScopedEnvironment implements ExtensionEnvironment, ConfigFactory, AutoCloseable {

    private final ExtensionDescriptor descriptor;
    private final Path directory;
    private final ClassLoader classLoader;
    private final Logger logger;
    private final PlatformServer platform;
    private final TextFormatter text;
    private final ActionParser<Audience> actions;
    private final ScopedScheduler scheduler;
    private final ScopedCommandRegistry commands;
    private final ScopedListenerRegistry listeners;
    private final ScopedPlaceholderRegistry placeholders;
    private ScopedExtensionService extensionServices;

    protected ScopedEnvironment(            final @NotNull ExtensionContext context,
            final @NotNull ExtensionDescriptor descriptor,
            final @NotNull Path directory,
            final @NotNull ClassLoader classLoader
    ) {

        this.descriptor = descriptor;
        this.directory = directory;
        this.classLoader = classLoader;
        this.logger = context.logger(descriptor.name());
        this.platform = context.platform();
        this.text = context.text();
        this.actions = context.actions();
        this.scheduler = new ScopedScheduler(context.scheduler());
        this.commands = new ScopedCommandRegistry(context.commands(), logger, descriptor.name());
        this.listeners = new ScopedListenerRegistry(context.events(), context.listeners().createExtensionScope(logger));
        this.placeholders = new ScopedPlaceholderRegistry(context.placeholders());

    }

    /**
     * The metadata file the extension was loaded from.
     *
     * @return the descriptor
     */
    public @NotNull ExtensionDescriptor descriptor() {
        return descriptor;

    }

    public void attachServices(            final ScopedExtensionService services
    ) {

        this.extensionServices = services;

    }

    @Override
    public @NotNull ExtensionService services() {
        return extensionServices;

    }

    @Override
    public boolean dependencyAvailable(            final @NotNull ExtensionId id
    ) {

        return extensionServices.available(id);

    }

    @Override
    public @NotNull String name() {
        return descriptor.name();

    }

    @Override
    public @NotNull Path directory() {
        return directory;

    }

    @Override
    public @NotNull Logger logger() {
        return logger;

    }

    @Override
    public @NotNull PlatformServer platform() {
        return platform;

    }

    @Override
    public @NotNull TaskScheduler<PlatformPlayer> scheduler() {
        return scheduler;

    }

    @Override
    public @NotNull ScopedCommandRegistry commands() {
        return commands;

    }

    @Override
    public @NotNull ScopedListenerRegistry listeners() {
        return listeners;

    }

    @Override
    public @NotNull ScopedPlaceholderRegistry placeholders() {
        return placeholders;

    }

    @Override
    public @NotNull TextFormatter text() {
        return text;

    }

    @Override
    public @NotNull ActionParser<Audience> actions() {
        return actions;

    }

    @Override
    public @NotNull ConfigFactory configs() {
        return this;

    }

    @Override
    public <T> ConfigFile.@NotNull Builder<T> file(            final @NotNull String name,
            final @NotNull Class<T> type
    ) {

        return ConfigFile.builder(type)
                .path(directory.resolve(name))
                .defaults(classLoader, name)
                .logger(logger);

    }

    @Override
    public void close() {
        closeQuietly(commands);
        closeQuietly(listeners);
        closeQuietly(placeholders);
        closeQuietly(scheduler);

    }

    private void closeQuietly(            final @NotNull AutoCloseable resource
    ) {

        try {
            resource.close();
        } catch (final Exception failure) {
            logger.warn("Could not clean up after {}", name(), failure);
        }

    }

}
