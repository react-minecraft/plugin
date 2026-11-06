package com.g4vrk.react.api.rcp.internal.loader;

import com.g4vrk.react.api.rcp.RCPLifecycle;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.exception.InvalidRCPException;
import com.g4vrk.react.api.rcp.internal.descriptor.RCPDescriptor;
import com.g4vrk.react.api.rcp.internal.environment.RCPEnvironmentFactory;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarRCPLoaderTest {

    public static final class Sample implements RCPLifecycle {

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }

    }

    private static final class Factory implements RCPEnvironmentFactory {

        private final List<RCPMetadata> created = new ArrayList<>();
        private final List<RCPEnvironment> released = new ArrayList<>();

        @Override
        public @NotNull RCPEnvironment create(
                final @NotNull RCPMetadata metadata,
                final @NotNull Path directory,
                final @NotNull Logger logger
        ) {
            created.add(metadata);
            return (RCPEnvironment) Proxy.newProxyInstance(
                    RCPEnvironment.class.getClassLoader(),
                    new Class<?>[]{RCPEnvironment.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "toString" -> metadata.name();
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
        }

        @Override
        public void release(final @NotNull RCPEnvironment environment) {
            released.add(environment);
        }

    }

    @TempDir
    Path directory;

    private Path jar(final String name, final String mainClass, final int apiVersion) throws IOException {
        final String metadata = "name: " + name + "\nversion: 1.2.0\nmainClass: " + mainClass
                + "\napi-version: " + apiVersion + "\n";
        final Path jar = directory.resolve("sample.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("rcp-metadata.yml"));
            zip.write(metadata.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }

    private JarRCPLoader loader(final Factory factory) {
        return new JarRCPLoader(
                directory.resolve("rcp").toFile(),
                new RCPDescriptor(),
                metadata -> LoggerFactory.getLogger(metadata.name()),
                factory,
                "rcp-metadata.yml");
    }

    @Test
    void loadsTheMainClassAndGivesItsOwnDirectory() throws Exception {
        final Factory factory = new Factory();
        final Path jar = jar("sample", Sample.class.getName(), 1);

        try (JarRCPLoader loader = loader(factory)) {
            final RCPLifecycle lifecycle = loader.load(jar);

            assertInstanceOf(Sample.class, lifecycle);
            assertEquals(new RCPMetadata("sample", "1.2.0", Sample.class.getName(), 1), factory.created.get(0));
            assertTrue(Files.isDirectory(directory.resolve("rcp").resolve("sample")));

            loader.release(lifecycle);
            assertEquals(1, factory.released.size());
        }
        assertEquals(1, factory.released.size());
    }

    @Test
    void closeReleasesWhatIsStillLoaded() throws Exception {
        final Factory factory = new Factory();
        final JarRCPLoader loader = loader(factory);
        loader.load(jar("sample", Sample.class.getName(), 1));

        loader.close();

        assertEquals(1, factory.released.size());
    }

    @Test
    void otherApiVersionIsRefused() throws Exception {
        final Path jar = jar("sample", Sample.class.getName(), 99);

        try (JarRCPLoader loader = loader(new Factory())) {
            assertThrows(InvalidRCPException.class, () -> loader.load(jar));
        }
    }

    @Test
    void nameThatLeavesTheDirectoryIsRefused() throws Exception {
        final Factory factory = new Factory();
        final Path jar = jar("../escape", Sample.class.getName(), 1);

        try (JarRCPLoader loader = loader(factory)) {
            assertThrows(InvalidRCPException.class, () -> loader.load(jar));
        }
        assertTrue(factory.created.isEmpty());
    }

    @Test
    void missingMainClassReleasesTheEnvironment() throws Exception {
        final Factory factory = new Factory();
        final Path jar = jar("sample", "com.example.Missing", 1);

        try (JarRCPLoader loader = loader(factory)) {
            assertThrows(InvalidRCPException.class, () -> loader.load(jar));
        }
        assertEquals(1, factory.released.size());
    }

}
