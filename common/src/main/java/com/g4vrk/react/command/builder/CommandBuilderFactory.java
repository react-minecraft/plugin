package com.g4vrk.react.command.builder;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

/**
 * Creates Incendo Cloud command builders bound to React's root command and permission.
 */
@RequiredArgsConstructor
@Accessors(fluent = true)
public final class CommandBuilderFactory {

    @Getter
    private final CommandManager<PlatformSender> manager;

    private final String label;
    private final String permission;
    private final String description;
    private final String[] aliases;

    public @NotNull Command.Builder<PlatformSender> create() {

        return this.manager.commandBuilder(
                this.label,
                Description.of(this.description),
                this.aliases
        ).permission(permission);

    }

}
