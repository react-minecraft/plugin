package com.g4vrk.react.api.addon;

import com.g4vrk.react.api.extension.AddonEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaAddonResourceTest {

    @TempDir
    Path directory;

    @Test
    void savesNestedResourcesAndHonorsReplacement() throws Exception {

        final TestAddon addon = addon();
        addon.saveResource("nested/config.yml", false);
        final Path path = directory.resolve("nested/config.yml");
        assertEquals("default", Files.readString(path));

        Files.writeString(path, "owner");
        addon.saveResource("nested/config.yml", false);
        assertEquals("owner", Files.readString(path));
        addon.saveResource("nested/config.yml", true);
        assertEquals("default", Files.readString(path));

    }

    @Test
    void rejectsPathsOutsideAddonDirectory() {

        assertThrows(IllegalArgumentException.class, () -> addon().saveResource("../outside.yml", true));

    }

    private TestAddon addon() {

        final AddonEnvironment environment = (AddonEnvironment) Proxy.newProxyInstance(
                AddonEnvironment.class.getClassLoader(),
                new Class<?>[]{AddonEnvironment.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "directory" -> directory;
                    case "logger" -> LoggerFactory.getLogger(JavaAddonResourceTest.class);
                    default -> null;
                }
        );
        final TestAddon addon = new TestAddon();
        addon.init(environment, getClass().getClassLoader());
        return addon;

    }

    private static final class TestAddon extends JavaAddon {

        @Override
        public InputStream getResource(final String name) {

            return new ByteArrayInputStream("default".getBytes(StandardCharsets.UTF_8));

        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }

    }

}
