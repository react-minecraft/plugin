package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.React;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.config.lang.Language;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

/**
 * Lists the check identifiers loaded from the current configuration.
 */
public final class CheckListArgument extends LocalArgument {

    private final CommandBuilderFactory builderFactory;

    public CheckListArgument(final @NotNull CommandBuilderFactory builderFactory) {

        this.builderFactory = builderFactory;

    }

    @Override
    public @NotNull Command.Builder<PlatformSender> build() {

        return builderFactory.create()
                .literal("checks")
                .handler(context -> {
                    final PlatformSender sender = context.sender();
                    final String checks = String.join(", ", React.INSTANCE.configs().checks().keySet());
                    final Language language = React.INSTANCE.language();
                    final String message;
                    if (checks.isEmpty()) {
                        message = language == Language.RU ? "Проверки не настроены." : "No checks are configured.";
                    } else {
                        final String label = language == Language.RU ? "Проверки: " : "Checks: ";
                        message = label + checks;
                    }
                    sender.sendMessage(Component.text(message));
                });

    }

}
