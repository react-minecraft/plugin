package com.g4vrk.react.extension;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.config.ConfigFile;
import com.g4vrk.react.api.extension.ConfigFactory;
import com.g4vrk.react.api.extension.ExtensionEnvironment;
import com.g4vrk.react.api.extension.ExtensionId;
import com.g4vrk.react.api.extension.ExtensionServices;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.plugin.Plugin;
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
    private final Plugin plugin;
    private final TextFormatter text;
    private final ActionParser<Audience> actions;
    private final ScopedScheduler scheduler;
    private final ScopedCommands commands;
    private final ScopedListeners listeners;
    private final ScopedPlaceholders placeholders;
    private ScopedExtensionServices extensionServices;

    protected ScopedEnvironment(
            final @NotNull ExtensionContext context,
            final @NotNull ExtensionDescriptor descriptor,
            final @NotNull Path directory,
            final @NotNull ClassLoader classLoader
    ) {
        this.descriptor = descriptor;
        this.directory = directory;
        this.classLoader = classLoader;
        this.logger = context.logger(descriptor.name());
        this.plugin = context.plugin();
        this.text = context.text();
        this.actions = context.actions();
        this.scheduler = new ScopedScheduler(context.scheduler());
        this.commands = new ScopedCommands(context.commands(), logger, descriptor.name());
        this.listeners = new ScopedListeners(plugin, context.events(), logger);
        this.placeholders = new ScopedPlaceholders(context.placeholders());
    }

    /**
     * The metadata file the extension was loaded from.
     *
     * @return the descriptor
     */
    public @NotNull ExtensionDescriptor descriptor() {
        return descriptor;
    }

    void attachServices(final ScopedExtensionServices services) {

        this.extensionServices = services;

    }

    @Override
    public @NotNull ExtensionServices services() {

        return extensionServices;

    }

    @Override
    public boolean dependencyAvailable(final @NotNull ExtensionId id) {

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
    public @NotNull Plugin plugin() {
        return plugin;
    }

    @Override
    public @NotNull Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public @NotNull ScopedCommands commands() {
        return commands;
    }

    @Override
    public @NotNull ScopedListeners listeners() {
        return listeners;
    }

    @Override
    public @NotNull ScopedPlaceholders placeholders() {
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
    public <T> ConfigFile.@NotNull Builder<T> file(final @NotNull String name, final @NotNull Class<T> type) {
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

    private void closeQuietly(final @NotNull AutoCloseable resource) {
        try {
            resource.close();
        } catch (final Exception failure) {
            logger.warn("Could not clean up after {}", name(), failure);
        }
    }

}
