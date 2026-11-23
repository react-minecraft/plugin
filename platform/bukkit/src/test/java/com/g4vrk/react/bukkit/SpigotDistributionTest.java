package com.g4vrk.react.bukkit;

import com.g4vrk.react.bukkit.sender.BukkitPlatformSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the Bukkit artifact declares a standard Bukkit plugin entry point.
 */
final class SpigotDistributionTest {

    @Test
    void descriptorTargetsBukkitPlugin() throws IOException {

        assertTrue(JavaPlugin.class.isAssignableFrom(SpigotPlugin.class));
        try (InputStream descriptor = getClass().getResourceAsStream("/plugin.yml")) {
            assertTrue(descriptor != null, "plugin.yml must be packaged in the Bukkit distribution");
            final String content = new String(descriptor.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains("main: com.g4vrk.react.bukkit.SpigotPlugin"));
        }

    }

    @Test
    void spigotSenderReceivesAdventureTextThroughLegacyApi() {

        final AtomicReference<String> sentMessage = new AtomicReference<>();
        final CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendMessage") && arguments != null && arguments.length == 1) {
                        sentMessage.set((String) arguments[0]);
                    }
                    return null;
                }
        );

        new BukkitPlatformSender(sender).sendMessage(Component.text("hello", NamedTextColor.RED));

        assertEquals("§chello", sentMessage.get());

    }

}
