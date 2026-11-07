package com.g4vrk.react.config;

import com.g4vrk.react.config.model.ActionsConfig;
import com.g4vrk.react.config.model.HistoryConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactConfigsMigrationTest {

    @Test
    void movesLegacyFormatsIntoActions(@TempDir final Path directory) throws Exception {
        copy("legacy/main-config.yml", directory.resolve("main-config.yml"));
        copy("legacy/actions.yml", directory.resolve("actions.yml"));
        Files.writeString(directory.resolve("main-config.yml"), Files.readString(directory.resolve("main-config.yml"))
                .replace("<white>провалил", "<white>custom"));

        final ReactConfigs configs = new ReactConfigs(directory, "ru", NOPLogger.NOP_LOGGER);
        configs.load();

        final ActionsConfig actions = configs.actions();
        assertEquals(1, actions.alerts().alert().size());
        assertTrue(actions.alerts().alert().get(0).startsWith("[msg] "));
        assertTrue(actions.alerts().alert().get(0).contains("<white>custom"));
        assertTrue(actions.alerts().alert().get(0).contains("<details>"));
        assertTrue(actions.monitor().message().get(0).contains("Аналитика"));
        assertTrue(actions.monitor().onEnable().get(0).contains("Мониторинг аналитики"));
        assertFalse(actions.monitor().onEnablePlayers().isEmpty());
        assertFalse(actions.alerts().onEnablePlayers().isEmpty());

        final String main = Files.readString(directory.resolve("main-config.yml"));
        assertFalse(main.contains("format:"));
        assertTrue(main.contains("config-version: 2"));
        assertFalse(Files.readString(directory.resolve("actions.yml")).contains("verbose:"));
    }

    @Test
    void turnsBracePlaceholdersIntoTags(@TempDir final Path directory) throws Exception {
        copy("legacy/history.yml", directory.resolve("history.yml"));

        final ReactConfigs configs = new ReactConfigs(directory, "ru", NOPLogger.NOP_LOGGER);
        configs.load();

        final HistoryConfig.Format format = configs.history().format();
        final String header = String.join(" ", format.header());
        final String footer = String.join(" ", format.footer());

        assertTrue(header.contains("<player>"));
        assertTrue(header.contains("<page> / <pages>"));
        assertTrue(header.contains("<avg-probability>"));
        assertTrue(format.entryFormat().get(0).contains("<probability>"));
        assertTrue(footer.contains("<prev><hover:show_text:"));
        assertTrue(footer.contains("</next>"));
        assertFalse(footer.contains("click:"));
        assertFalse(Files.readString(directory.resolve("history.yml")).contains("{"));
    }

    private static void copy(final String resource, final Path target) throws IOException {
        try (InputStream in = ReactConfigsMigrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            Files.copy(in, target);
        }
    }

}
