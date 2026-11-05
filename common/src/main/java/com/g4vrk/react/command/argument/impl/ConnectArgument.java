package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.React;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

/** /react connect &lt;code&gt;: links this server with the code from its page on the site. */
public final class ConnectArgument extends LocalArgument {

    private final CommandBuilderFactory builderFactory;
    private final Scheduler scheduler;

    public ConnectArgument(@NotNull CommandBuilderFactory builderFactory, @NotNull Scheduler scheduler) {
        this.builderFactory = builderFactory;
        this.scheduler = scheduler;
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal("connect")
                .permission("react.command.connect")
                .required("code", StringParser.stringParser())
                .handler(context -> {
                    final CommandSender sender = context.sender();
                    final String code = context.get("code");
                    reply(sender, NamedTextColor.GRAY, text("Проверяем код...", "Checking the code..."));
                    React.INSTANCE.getInferenceServer().connect(code).whenComplete((result, failure) -> {
                        if (failure != null) {
                            reply(sender, NamedTextColor.RED, failed(failure.getMessage()));
                        } else if (result.apiKey() == null) {
                            reply(sender, NamedTextColor.RED, failed(result.detail()));
                        } else {
                            finish(sender, result);
                        }
                    });
                });
    }

    private void finish(final CommandSender sender, final InferenceServer.ConnectResult result) {
        try {
            React.INSTANCE.saveApiKey(result.apiKey());
        } catch (final Exception ex) {
            React.INSTANCE.getLogger().error("Could not save the server key", ex);
            reply(sender, NamedTextColor.RED, failed(ex.getMessage()));
            return;
        }
        React.INSTANCE.reloadAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                React.INSTANCE.getLogger().error("React reload failed after connecting", failure);
                reply(sender, NamedTextColor.RED, failed(failure.getMessage()));
                return;
            }
            reply(sender, NamedTextColor.GREEN, text("Подключено", "Connected") + ": " + result.server()
                    + " (" + result.modelNames() + ")");
        });
    }

    private static String failed(final String detail) {
        return text("Не удалось подключить", "Could not connect") + ": " + detail;
    }

    private static String text(final String russian, final String english) {
        return "RU".equalsIgnoreCase(React.INSTANCE.getLanguage().name()) ? russian : english;
    }

    private void reply(final CommandSender sender, final NamedTextColor color, final String message) {
        scheduler.schedule(() -> sender.sendMessage(Component.text(message).color(color)), TickSchedule.instant());
    }

}
