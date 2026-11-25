/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.update;

import com.g4vrk.react.extension.descriptor.ExtensionKind;
import com.g4vrk.react.update.checksum.ChecksumManifest;
import com.g4vrk.react.update.client.ReleaseClient;
import com.g4vrk.react.update.client.UpdateSecurityTestAccessor;
import com.g4vrk.react.update.stage.StagedUpdateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces updater cryptographic, size-limit, path-traversal, and atomic staging invariants.
 */
final class UpdateSecurityLockTest {

    private static final String HASH = "0".repeat(64);

    @Test
    void manifestRejectsPathTraversalAndIgnoresSourcesAndJavadocJars() {

        final String manifest = """
                # version: 2.0.0
                # api-version: 2
                %s  ../evil.jar
                %s  subdir/evil.jar
                %s  subdir\\evil.jar
                %s  react-paper-2.0.0-sources.jar
                %s  react-paper-2.0.0-javadoc.jar
                %s  react-paper-2.0.0.jar
                """.formatted(HASH, HASH, HASH, HASH, HASH, HASH);

        final ChecksumManifest parsed = ChecksumManifest.parse(manifest);
        assertEquals(Set.of("react-paper-2.0.0-sources.jar", "react-paper-2.0.0-javadoc.jar", "react-paper-2.0.0.jar"), parsed.files().keySet());
        assertEquals("react-paper-2.0.0.jar", parsed.jar("react-paper").getKey());

    }

    @Test
    void signatureVerifierRejectsForgedOrCorruptedSignatures() {

        final byte[] payload = "react-release-manifest".getBytes(StandardCharsets.UTF_8);
        assertThrows(
                ReleaseClient.VerificationException.class,
                () -> UpdateSecurityTestAccessor.verifySignature(payload, "not-valid-base64!!!".getBytes(StandardCharsets.US_ASCII))
        );
        assertThrows(
                ReleaseClient.VerificationException.class,
                () -> UpdateSecurityTestAccessor.verifySignature(payload, "AAAA".getBytes(StandardCharsets.US_ASCII))
        );
        final byte[] forged64Bytes = new byte[64];
        final String encoded = Base64.getEncoder().encodeToString(forged64Bytes);
        assertThrows(
                ReleaseClient.VerificationException.class,
                () -> UpdateSecurityTestAccessor.verifySignature(payload, encoded.getBytes(StandardCharsets.US_ASCII))
        );

    }

    @Test
    void limitedBodySubscriberCancelsStreamWhenLimitExceeded() {

        final CompletableFuture<byte[]> downstream = new CompletableFuture<>();
        final var subscriber = UpdateSecurityTestAccessor.limitedByteSubscriber(downstream, 8L);
        final AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(final long n) {

            }

            @Override
            public void cancel() {

                cancelled.set(true);

            }
        });

        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5})));
        assertFalse(cancelled.get());
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{6, 7, 8, 9})));

        assertTrue(cancelled.get());
        assertTrue(downstream.isCompletedExceptionally());
        assertThrows(ExecutionException.class, downstream::get);

    }

    @Test
    void stagedUpdateStoreAppliesDifferentAndSameFilenameUpdatesAndPrunesStaleFolders(@TempDir final Path dir) throws IOException {

        final StagedUpdateStore store = new StagedUpdateStore(dir.resolve("update"), NOPLogger.NOP_LOGGER);
        final Path addonsDir = dir.resolve("addons");
        Files.createDirectories(addonsDir);

        final Path oldJar = addonsDir.resolve("menu-addon-1.0.jar");
        Files.writeString(oldJar, "old");

        final Path stagedDir = store.stagedFolder(ExtensionKind.ADDON, "Menu");
        Files.createDirectories(stagedDir);
        Files.writeString(stagedDir.resolve("menu-addon-1.1.jar"), "new");

        final StringBuilder appliedKey = new StringBuilder();
        final Path updatedJar = store.apply(ExtensionKind.ADDON, "Menu", oldJar, appliedKey::append);
        assertEquals(addonsDir.resolve("menu-addon-1.1.jar"), updatedJar);
        assertEquals("new", Files.readString(updatedJar));
        assertFalse(Files.exists(oldJar));
        assertFalse(Files.exists(addonsDir.resolve("menu-addon-1.0.jar.bak")));
        assertEquals("addon:menu", appliedKey.toString());

        Files.createDirectories(stagedDir);
        Files.writeString(stagedDir.resolve("menu-addon-1.1.jar"), "same-name-update");
        appliedKey.setLength(0);
        final Path sameName = store.apply(ExtensionKind.ADDON, "Menu", updatedJar, appliedKey::append);
        assertEquals(updatedJar, sameName);
        assertEquals("same-name-update", Files.readString(sameName));
        assertEquals("addon:menu", appliedKey.toString());

        final Path staleDir = store.stagedFolder(ExtensionKind.ADDON, "RemovedAddon");
        Files.createDirectories(staleDir);
        Files.writeString(staleDir.resolve("old.jar"), "stale");
        store.prune(Set.of("plugin"));
        assertFalse(Files.exists(staleDir));

    }

}
