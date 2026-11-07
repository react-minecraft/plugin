package com.g4vrk.react.placeholder;

import com.g4vrk.react.api.player.TrackedPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimplePlaceholderRegistryTest {

    private final SimplePlaceholderRegistry registry = new SimplePlaceholderRegistry(NOPLogger.NOP_LOGGER);

    private final TrackedPlayer player = (TrackedPlayer) Proxy.newProxyInstance(
            TrackedPlayer.class.getClassLoader(),
            new Class<?>[]{TrackedPlayer.class},
            (proxy, method, arguments) -> method.getName().equals("name") ? "Steve" : null
    );

    @Test
    void resolvesTagsWithQueries() {
        registry.register("vl", (target, query) -> target.name() + "=" + query);
        registry.register("broken", (target, query) -> {
            throw new IllegalStateException("boom");
        });

        final String text = PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(
                "<react_vl:aim> <react_vl> <react_broken> <react_none>",
                registry.tags(player)
        ));

        assertEquals("Steve=aim Steve= <react_broken> <react_none>", text);
    }

    @Test
    void validatesKeys() {
        registry.register("vl", (target, query) -> "");

        assertThrows(IllegalArgumentException.class, () -> registry.register("vl", (target, query) -> ""));
        assertThrows(IllegalArgumentException.class, () -> registry.register("Bad_Key", (target, query) -> ""));
        assertNull(registry.resolve(player, "missing", ""));
    }

}
