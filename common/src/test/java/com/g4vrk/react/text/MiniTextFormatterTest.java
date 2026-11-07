package com.g4vrk.react.text;

import com.g4vrk.react.placeholder.SimplePlaceholderRegistry;
import com.g4vrk.text.LegacySerializers;
import org.slf4j.helpers.NOPLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniTextFormatterTest {

    private final MiniTextFormatter formatter = new MiniTextFormatter(
            LegacySerializers.INSTANCE,
            new SimplePlaceholderRegistry(NOPLogger.NOP_LOGGER)
    );

    @Test
    void mixesLegacyCodesWithResolvers() {
        final Component component = formatter.format(
                "<white>Page &7<page> <link>open</link>",
                Placeholder.unparsed("page", "2 <red>"),
                TagResolver.resolver("link", Tag.styling(ClickEvent.runCommand("/next")))
        );

        assertEquals("Page 2 <red> open", PlainTextComponentSerializer.plainText().serialize(component));
        assertTrue(component.toString().contains("/next"));
        assertTrue(component.toString().contains(NamedTextColor.GRAY.toString()));
    }

}
