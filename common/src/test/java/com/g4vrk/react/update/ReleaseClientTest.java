package com.g4vrk.react.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseClientTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void boundAcceptsMatchingRepositoryChannelAndVersion() throws ReleaseClient.VerificationException {

        final Checksums checksums = Checksums.parse("""
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

        final Checksums checksums = Checksums.parse("""
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
    void boundAcceptsLegacyChecksumsWithoutRepositoryOrChannelHeaders() throws ReleaseClient.VerificationException {

        final Checksums checksums = Checksums.parse("""
                # version: 2.0.0
                %s  react-paper-2.0.0.jar
                """.formatted(HASH));
        final ReleaseClient.Release release = new ReleaseClient.Release("https://example.invalid/", checksums);

        assertSame(release, ReleaseClient.bound(release, "react-minecraft/plugin", "release", "2.0.0"));

    }

}
