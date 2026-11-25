package com.g4vrk.react.api.config;

import com.g4vrk.react.api.config.migration.ConfigMigrationChain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies YAML loading, migration, and concurrent access guarantees of {@link ConfigFile}.
 */
class ConfigFileTest {

    private static final String DEFAULTS = """
            # bundled comment
            config-version: 2
            name: react
            limit: 5
            """;

    private static final ConfigMigrationChain RENAME = ConfigMigrationChain.builder()
            .step(root -> {
            })
            .step(root -> {
                root.node("limit").set(root.node("max").getInt());
                root.node("max").raw(null);
            })
            .build();

    @TempDir
    Path directory;

    private ConfigFile<Object> file(final Path path, final ConfigMigrationChain migrations) {

        return ConfigFile.builder(Object.class)
                .path(path)
                .defaults(() -> new ByteArrayInputStream(DEFAULTS.getBytes(StandardCharsets.UTF_8)))
                .migrations(migrations)
                .logger(LoggerFactory.getLogger(ConfigFileTest.class))
                .build();

    }

    @Test
    void missingFileIsCopiedWithComments() throws IOException {

        final Path path = directory.resolve("config.yml");

        final CommentedConfigurationNode root = file(path, RENAME).loadNode();

        assertEquals(5, root.node("limit").getInt());
        assertEquals(DEFAULTS, Files.readString(path));

    }

    @Test
    void legacyFileIsMigratedAndBackedUp() throws IOException {

        final Path path = directory.resolve("config.yml");
        Files.writeString(path, "name: owner\nmax: 9\n");

        final CommentedConfigurationNode root = file(path, RENAME).loadNode();

        assertEquals(9, root.node("limit").getInt());
        assertEquals("owner", root.node("name").getString());
        assertTrue(root.node("max").virtual());
        assertEquals("name: owner\nmax: 9\n", Files.readString(directory.resolve("config.yml.v0.bak")));
        final String saved = Files.readString(path);
        assertTrue(saved.contains("limit: 9"));
        assertTrue(saved.contains("config-version: 2"));
        assertFalse(saved.contains("max:"));

    }

    @Test
    void unchangedLayoutOnlyGetsVersionStamped() throws IOException {

        final Path path = directory.resolve("config.yml");
        final String owner = "# owner comment\nname: owner\nlimit: 3\n";
        Files.writeString(path, owner);

        file(path, ConfigMigrationChain.none()).loadNode();

        assertEquals(owner + "\nconfig-version: 1\n", Files.readString(path));
        assertFalse(Files.exists(directory.resolve("config.yml.v0.bak")));

    }

    @Test
    void missingKeysComeFromDefaultsWithoutTouchingTheFile() throws IOException {

        final Path path = directory.resolve("config.yml");
        final String owner = "config-version: 2\nname: owner\n";
        Files.writeString(path, owner);

        final CommentedConfigurationNode root = file(path, RENAME).loadNode();

        assertEquals(5, root.node("limit").getInt());
        assertEquals(owner, Files.readString(path));

    }

    @Test
    void newerFileFailsToLoad() throws IOException {

        final Path path = directory.resolve("config.yml");
        Files.writeString(path, "config-version: 7\n");

        assertThrows(ConfigurateException.class, () -> file(path, RENAME).loadNode());

    }

    @Test
    void malformedVersionsLeaveTheFileUntouched() throws IOException {

        final Path path = directory.resolve("config.yml");
        for (final String version : new String[]{"broken", "2147483648", "1.5"}) {
            final String original = "config-version: " + version + "\nmax: 9\n";
            Files.writeString(path, original);

            assertThrows(ConfigurateException.class, () -> file(path, RENAME).loadNode());
            assertEquals(original, Files.readString(path));
            assertFalse(Files.exists(directory.resolve("config.yml.v0.bak")));
        }

    }

    @Test
    void concurrentFirstLoadsSucceedWithoutTemporaryFilesLeftBehind() throws Exception {

        final Path path = directory.resolve("concurrent.yml");
        final ConfigFile<Object> config = file(path, RENAME);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<CommentedConfigurationNode>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return config.loadNode();
                }));
            }
            start.countDown();
            for (final Future<CommentedConfigurationNode> future : futures) {
                assertEquals(5, future.get().node("limit").getInt());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(DEFAULTS, Files.readString(path));
        try (var entries = Files.list(directory)) {
            assertEquals(1L, entries.count());
        }

    }

}
