package com.g4vrk.react.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExtensionClassLoaderTest {

    @Test
    void ownResourceWinsOverThePlugins(@TempDir final Path directory) throws Exception {
        final Path parentJar = jar(directory.resolve("plugin.jar"), "en/actions.yml", "plugin");
        final Path extensionJar = jar(directory.resolve("addon.jar"), "en/actions.yml", "addon");

        try (URLClassLoader parent = new URLClassLoader(new URL[]{parentJar.toUri().toURL()}, null);
             ExtensionClassLoader loader = new ExtensionClassLoader("test", extensionJar.toUri().toURL(), parent)) {
            assertEquals("addon", read(loader, "en/actions.yml"));
        }
    }

    @Test
    void missingResourceFallsBackToThePlugin(@TempDir final Path directory) throws Exception {
        final Path parentJar = jar(directory.resolve("plugin.jar"), "shared.yml", "plugin");
        final Path extensionJar = jar(directory.resolve("addon.jar"), "own.yml", "addon");

        try (URLClassLoader parent = new URLClassLoader(new URL[]{parentJar.toUri().toURL()}, null);
             ExtensionClassLoader loader = new ExtensionClassLoader("test", extensionJar.toUri().toURL(), parent)) {
            assertEquals("plugin", read(loader, "shared.yml"));
        }
    }

    private static String read(
            final ClassLoader loader,
            final String name
    ) throws Exception {
        try (InputStream in = loader.getResourceAsStream(name)) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path jar(
            final Path path,
            final String entry,
            final String content
    ) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new JarEntry(entry));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return path;
    }

}
