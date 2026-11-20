package com.g4vrk.react.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionManagerShutdownTest {

    @Test
    void shutdownDuringUpdateCheckSkipsExtensionLoading(@TempDir final Path directory) throws Exception {
        final ExtensionContext context = new ExtensionContext(null, directory, LoggerFactory.getLogger(getClass()),
                null, null, null, null, null, null, null, null, null, null);
        final ExtensionManager manager = new ExtensionManager(context, StagedUpdates.NONE);
        final CountDownLatch checking = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        try {
            final var started = manager.start(() -> {
                checking.countDown();
                try {
                    release.await();
                } catch (final InterruptedException failure) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(checking.await(5, TimeUnit.SECONDS));
            manager.stopLoading();
            release.countDown();
            started.get(5, TimeUnit.SECONDS);
            assertFalse(Files.exists(ExtensionKind.ADDON.jars(directory)));
            assertTrue(manager.extensions().isEmpty());
        } finally {
            release.countDown();
            manager.close();
        }
    }

}
