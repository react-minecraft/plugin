package com.g4vrk.react.extension;

import com.g4vrk.react.api.extension.command.CommandRegistrar;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Commands owned by one extension on the plugin's shared Cloud manager. */
public final class ScopedCommands implements CommandRegistrar, AutoCloseable {

    private static final String PERMISSION_PREFIX = "react.command.";
    private final CommandManager<CommandSender> manager;
    private final Logger logger;
    private final String owner;
    private final Map<String, List<Command<CommandSender>>> commands = new LinkedHashMap<>();
    private boolean closed;

    public ScopedCommands(
            final @NotNull CommandManager<CommandSender> manager,
            final @NotNull Logger logger,
            final @NotNull String owner
    ) {
        this.manager = manager;
        this.logger = logger;
        this.owner = owner;
    }

    @Override
    public @NotNull Command.Builder<CommandSender> command(final @NotNull String label, final @NotNull String @NotNull ... aliases) {
        return this.manager.commandBuilder(label, Description.of("Command of '" + this.owner + "'"), aliases)
                .permission(PERMISSION_PREFIX + label);
    }

    @Override
    public void register(final @NotNull String label, final @NotNull Command.Builder<CommandSender> builder) {
        final Command<CommandSender> command = builder.build();
        if (!label.equals(command.rootComponent().name())) {
            throw new IllegalArgumentException("Command label must match its Cloud root");
        }
        synchronized (this.manager) {
            if (this.closed) {
                throw new IllegalStateException("Command scope is closed: " + this.owner);
            }
            this.manager.command(command);
            this.commands.computeIfAbsent(label, ignored -> new ArrayList<>()).add(command);
        }
    }

    @Override
    public boolean unregister(final @NotNull String label) {
        synchronized (this.manager) {
            final List<Command<CommandSender>> owned = this.commands.get(label);
            if (owned == null) {
                return false;
            }
            // Cloud exposes removal of roots, not individual leaves. Rebuild the root with the
            // original Command objects of the plugin and other extensions, including their aliases.
            final List<Command<CommandSender>> original = this.manager.commands().stream()
                    .filter(command -> command.rootComponent().name().equals(label))
                    .toList();
            final List<Command<CommandSender>> retained = original.stream()
                    .filter(command -> owned.stream().noneMatch(candidate -> candidate == command))
                    .toList();
            try {
                this.manager.deleteRootCommand(label);
                for (final Command<CommandSender> command : retained) {
                    this.manager.command(command);
                }
                this.commands.remove(label);
                return true;
            } catch (final RuntimeException failure) {
                this.logger.error("Could not remove commands under /{} of extension '{}'", label, this.owner, failure);
                throw failure;
            }
        }
    }

    @Override
    public void close() {
        synchronized (this.manager) {
            this.closed = true;
            for (final String root : List.copyOf(this.commands.keySet())) {
                unregister(root);
            }
        }
    }
}
