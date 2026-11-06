package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.React;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

/** /react connect check: asks the site which models this server's key can use right now. */
public final class ConnectCheckArgument extends LocalArgument {

    private final CommandBuilderFactory builderFactory;
    private final Scheduler scheduler;

    public ConnectCheckArgument(@NotNull CommandBuilderFactory builderFactory, @NotNull Scheduler scheduler) {
        this.builderFactory = builderFactory;
        this.scheduler = scheduler;
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal("connect")
                .literal("check")
                .permission("react.command.connect")
                .handler(context -> {
                    final CommandSender sender = context.sender();
                    reply(sender, NamedTextColor.GRAY, text("Проверяем подключение...", "Checking the connection..."));
                    React.INSTANCE.getInferenceServer().checkConnection().whenComplete((status, failure) -> {
                        if (failure != null) {
                            reply(sender, NamedTextColor.RED, failed(failure.getMessage()));
                        } else if (status.problem() != null) {
                            reply(sender, NamedTextColor.RED, failed(status.problem()));
                        } else {
                            print(sender, status);
                        }
                    });
                });
    }

    private void print(final CommandSender sender, final InferenceServer.ConnectionStatus status) {
        reply(sender, NamedTextColor.GREEN, text("Подключено", "Connected") + ": " + status.server());
        for (final ModelId model : ModelId.values()) {
            final Boolean enabled = status.models().get(model);
            if (enabled == null) {
                reply(sender, NamedTextColor.DARK_GRAY, "- " + model.displayName() + ": " + text("нет доступа", "no access"));
            } else if (enabled) {
                reply(sender, NamedTextColor.GREEN, "+ " + model.displayName() + ": " + text("работает", "active"));
            } else {
                reply(sender, NamedTextColor.YELLOW, "- " + model.displayName() + ": "
                        + text("выключена или нет кредитов", "turned off or out of credits"));
            }
        }
    }

    private static String failed(final String detail) {
        return text("Подключение не работает", "The connection is not working") + ": " + detail;
    }

    private static String text(final String russian, final String english) {
        return "RU".equalsIgnoreCase(React.INSTANCE.getLanguage().name()) ? russian : english;
    }

    private void reply(final CommandSender sender, final NamedTextColor color, final String message) {
        scheduler.schedule(() -> sender.sendMessage(Component.text(message).color(color)), TickSchedule.instant());
    }

}
