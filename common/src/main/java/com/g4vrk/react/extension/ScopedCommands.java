package com.g4vrk.react.extension;

import com.g4vrk.react.api.extension.command.CommandRegistrar;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The commands of one extension, registered on the plugin's own Cloud manager.
 *
 * <p>Roots the extension creates are remembered so {@link #close()} can remove them again; a
 * command added under the plugin's existing roots is left alone, since deleting the root would
 * delete the plugin's own tree with it.</p>
 */
public final class ScopedCommands implements CommandRegistrar, AutoCloseable {

    private static final String PERMISSION_PREFIX = "react.command.";

    private final CommandManager<CommandSender> manager;
    private final Logger logger;
    private final String owner;
    private final Set<String> roots = ConcurrentHashMap.newKeySet();
    private final Set<String> pluginRoots;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public ScopedCommands(
            final @NotNull CommandManager<CommandSender> manager,
            final @NotNull Logger logger,
            final @NotNull String owner
    ) {
        this.manager = manager;
        this.logger = logger;
        this.owner = owner;
        this.pluginRoots = Set.copyOf(manager.rootCommands());
    }

    @Override
    public @NotNull Command.Builder<CommandSender> command(final @NotNull String label, final @NotNull String @NotNull ... aliases) {
        return this.manager.commandBuilder(label, Description.of("Command of '" + this.owner + "'"), aliases)
                .permission(PERMISSION_PREFIX + label);
    }

    @Override
    public void register(final @NotNull String label, final @NotNull Command.Builder<CommandSender> command) {
        if (!this.pluginRoots.contains(label) && this.roots.add(label) && this.reported.add(label)) {
            this.logger.info("Extension '{}' registers the command /{}", this.owner, label);
        }

        this.manager.command(command);
    }

    @Override
    public boolean unregister(final @NotNull String label) {
        if (!this.roots.remove(label)) {
            return false;
        }

        try {
            this.manager.deleteRootCommand(label);
            return true;
        } catch (final Throwable failure) {
            this.logger.warn("Could not remove /{} of extension '{}'", label, this.owner, failure);
            return false;
        }
    }

    @Override
    public void close() {
        for (final String root : List.copyOf(this.roots)) {
            unregister(root);
        }

        this.roots.clear();
        this.reported.clear();
    }

}
