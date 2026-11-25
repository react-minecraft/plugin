package com.g4vrk.react.update.client;

import com.g4vrk.react.update.checksum.ChecksumManifest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies metadata validation and repository binding in {@link ReleaseClient}.
 */
class ReleaseClientTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void boundAcceptsMatchingRepositoryChannelAndVersion() throws ReleaseClient.VerificationException {

        final ChecksumManifest checksums = ChecksumManifest.parse("""
                # repository: react-minecraft/plugin
                # channel: release
                # version: 2.0.0
                %s  react-paper-2.0.0.jar
                """.formatted(HASH));
        final ReleaseClient.Release release = new ReleaseClient.Release("https://example.invalid/", checksums);

        assertSame(release, ReleaseClient.bound(release, "react-minecraft/plugin", "release", "2.0.0"));
        assertSame(release, ReleaseClient.bound(release, "REACT-MINECRAFT/PLUGIN", "RELEASE", null));

    }

    @Test
    void boundRejectsMismatchedRepositoryChannelOrVersion() {

        final ChecksumManifest checksums = ChecksumManifest.parse("""
                # repository: react-minecraft/hologram-addon
                # channel: nightly
                # version: 1.2.0
                %s  hologram-addon-1.2.0.jar
                """.formatted(HASH));
        final ReleaseClient.Release release = new ReleaseClient.Release("https://example.invalid/", checksums);

        assertThrows(
                ReleaseClient.VerificationException.class,
                () -> ReleaseClient.bound(release, "react-minecraft/plugin", "nightly", "1.2.0")
        );
        assertThrows(
                ReleaseClient.VerificationException.class,
                () -> ReleaseClient.bound(release, "react-minecraft/hologram-addon", "release", "1.2.0")
        );
        assertThrows(
                ReleaseClient.VerificationException.class,
                () -> ReleaseClient.bound(release, "react-minecraft/hologram-addon", "nightly", "1.3.0")
        );

    }

    @Test
    void boundRejectsMissingRepositoryOrChannelHeaders() {

        final ChecksumManifest missingRepository = ChecksumManifest.parse("""
                # channel: release
                # version: 2.0.0
                %s  react-paper-2.0.0.jar
                """.formatted(HASH));
        final ChecksumManifest missingChannel = ChecksumManifest.parse("""
                # repository: react-minecraft/plugin
                # version: 2.0.0
                %s  react-paper-2.0.0.jar
                """.formatted(HASH));

        assertThrows(
                ReleaseClient.VerificationException.class,
                () -> ReleaseClient.bound(new ReleaseClient.Release("https://example.invalid/", missingRepository), "react-minecraft/plugin", "release", "2.0.0")
        );
        assertThrows(
                ReleaseClient.VerificationException.class,
                () -> ReleaseClient.bound(new ReleaseClient.Release("https://example.invalid/", missingChannel), "react-minecraft/plugin", "release", "2.0.0")
        );

    }

    @Test
    void releaseDowngradeRejectsStrictlyLowerNumericReleasesOnly() {

        assertTrue(ReleaseClient.isReleaseDowngrade("2.1.0", "2.0.0"));
        assertTrue(ReleaseClient.isReleaseDowngrade("2.10.0", "2.2.0"));
        assertTrue(ReleaseClient.isReleaseDowngrade("2.0.1", "2.0"));

        assertFalse(ReleaseClient.isReleaseDowngrade("2.0.0", "2.0.0"));
        assertFalse(ReleaseClient.isReleaseDowngrade("2.0", "2.0.0"));
        assertFalse(ReleaseClient.isReleaseDowngrade("2.2.0", "2.10.0"));
        assertTrue(ReleaseClient.isReleaseDowngrade("2.0.0-6d81e55", "1.9.0"));
        assertTrue(ReleaseClient.isReleaseDowngrade("1.0.3", "1.0.2-396c525"));
        assertTrue(ReleaseClient.isReleaseDowngrade("1.0.3-59a6ddf", "1.0.2-396c525"));
        assertFalse(ReleaseClient.isReleaseDowngrade("9.9.9-local", "1.0.0"));
        assertFalse(ReleaseClient.isReleaseDowngrade("2.0.0", "2.0.0-abc1234"));

    }

}
