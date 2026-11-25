package com.g4vrk.react.extension.scope;

import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.key.CloudKey;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commands owned by one extension on React's shared platform-agnostic Cloud manager.
 */
public final class ScopedCommandRegistry implements CommandRegistrar, AutoCloseable {

    private static final String PERMISSION_PREFIX = "react.command.";

    private final PlatformCommandBridge platformCommands;
    private final CommandManager<PlatformSender> manager;
    private final Logger logger;
    private final String owner;
    private final Map<String, List<Command<PlatformSender>>> commands = new LinkedHashMap<>();
    private boolean closed;

    public ScopedCommandRegistry(
            final @NotNull PlatformCommandBridge platformCommands,
            final @NotNull Logger logger,
            final @NotNull String owner
    ) {

        this.platformCommands = platformCommands;
        this.manager = platformCommands.manager();
        this.logger = logger;
        this.owner = owner;

    }

    @Override
    public @NotNull Command.Builder<PlatformSender> command(
            final @NotNull String label,
            final @NotNull String @NotNull ... aliases
    ) {

        return this.manager.commandBuilder(label, Description.of("Command of '" + this.owner + "'"), aliases)
                .permission(PERMISSION_PREFIX + label);

    }

    @Override
    public void register(
            final @NotNull String label,
            final @NotNull Command.Builder<PlatformSender> builder
    ) {

        final Command<PlatformSender> command = builder.build();
        if (!label.equals(command.rootComponent().name())) {
            throw new IllegalArgumentException("Command label must match its Cloud root");
        }
        synchronized (this.manager) {
            if (this.closed) {
                throw new IllegalStateException("Command scope is closed: " + this.owner);
            }
            this.manager.command(command);
            this.commands.computeIfAbsent(label, ignored -> new ArrayList<>()).add(command);
            this.platformCommands.onCommandsChanged(label);
        }

    }

    @Override
    public boolean unregister(
            final @NotNull String label
    ) {

        synchronized (this.manager) {
            final List<Command<PlatformSender>> owned = this.commands.get(label);
            if (owned == null) {
                return false;
            }
            /*
             * Cloud exposes removal of roots, not individual leaves. Rebuild the root with the
             * original Command objects of the plugin and other extensions, including their aliases.
             */
            final List<Command<PlatformSender>> retained = new ArrayList<>();
            for (final Command<PlatformSender> command : this.manager.commands()) {
                if (command.rootComponent().name().equals(label) && !containsIdentity(owned, command)) {
                    retained.add(command);
                }
            }
            try {
                this.manager.deleteRootCommand(label);
                for (final Command<PlatformSender> command : retained) {
                    this.manager.command(command);
                }
                this.commands.remove(label);
                this.platformCommands.onCommandsChanged(label);
                return true;
            } catch (final RuntimeException failure) {
                this.logger.error("Could not remove commands under /{} of extension '{}'", label, this.owner, failure);
                throw failure;
            }
        }

    }

    @Override
    public CommandComponent.@NotNull Builder<PlatformSender, PlatformPlayer> singlePlayerArgument(
            final @NotNull CloudKey<PlatformPlayer> key,
            final boolean required
    ) {

        return this.platformCommands.singlePlayerArgument(key, required);

    }

    @Override
    public CommandComponent.@NotNull Builder<PlatformSender, Collection<PlatformPlayer>> multiplePlayersArgument(
            final @NotNull CloudKey<Collection<PlatformPlayer>> key,
            final boolean required
    ) {

        return this.platformCommands.multiplePlayersArgument(key, required);

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

    private static boolean containsIdentity(
            final @NotNull List<Command<PlatformSender>> list,
            final @NotNull Command<PlatformSender> target
    ) {

        for (final Command<PlatformSender> candidate : list) {
            if (candidate == target) {
                return true;
            }
        }
        return false;

    }

}
