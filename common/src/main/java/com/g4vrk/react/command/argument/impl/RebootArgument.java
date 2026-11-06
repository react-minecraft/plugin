package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.React;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.reboot.PluginReboot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

/**
 * /react reboot: unloads React and loads it again from the jar in plugins/, so a replaced jar
 * or added and removed addons take effect without restarting the server.
 */
public final class RebootArgument extends LocalArgument {

    private final CommandBuilderFactory builderFactory;

    public RebootArgument(@NotNull CommandBuilderFactory builderFactory) {
        this.builderFactory = builderFactory;
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal("reboot")
                .permission("react.command.reboot")
                .handler(context -> {
                    final CommandSender sender = context.sender();
                    final Plugin plugin = React.INSTANCE.getPlugin();
                    final PluginReboot reboot;
                    try {
                        reboot = PluginReboot.prepare(plugin, sender, new PluginReboot.Messages(
                                prefix() + text("Плагин выгружен, загружаем новый jar...", "Plugin unloaded, loading the new jar..."),
                                prefix() + text("Плагин перезапущен:", "Plugin rebooted:"),
                                prefix() + text("Перезапуск не удался.", "Reboot failed.")));
                    } catch (final IllegalStateException refused) {
                        React.INSTANCE.getLogger().warn("React reboot refused: {}", refused.getMessage());
                        sender.sendMessage(Component.text(text("Перезапуск отменён: ", "Reboot cancelled: ")
                                + refused.getMessage()).color(NamedTextColor.RED));
                        return;
                    }
                    sender.sendMessage(Component.text(text("Перезапускаем React из ", "Rebooting React from ")
                            + reboot.jar().getName() + " (" + reboot.newVersion() + ")...").color(NamedTextColor.GRAY));
                    // Run outside the command dispatch: disabling React tears down this command manager.
                    Bukkit.getScheduler().runTask(plugin, reboot);
                });
    }

    private static String prefix() {
        return "§7[§fReact§7] §f";
    }

    private static String text(final String russian, final String english) {
        return "RU".equalsIgnoreCase(React.INSTANCE.getLanguage().name()) ? russian : english;
    }

}
