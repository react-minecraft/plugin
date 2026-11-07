package com.g4vrk.react.command.builder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public final class CommandBuilderFactory {

    @Getter
    private final CommandManager<CommandSender> manager;

    private final String label;
    private final String permission;
    private final String description;
    private final String[] aliases;

    public @NotNull Command.Builder<CommandSender> create() {
        return this.manager.commandBuilder(
                this.label,
                Description.of(this.description),
                this.aliases
        ).permission(permission);
    }

}
