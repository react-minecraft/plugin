package com.g4vrk.react.update.checksum;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChecksumsTest {

    private static final String JAR_HASH = "a".repeat(64);
    private static final String SOURCES_HASH = "B".repeat(64);

    private static final String RELEASE = """
            # React RCP checksums
            #
            # version: 1.4.0
            # channel: release
            # commit: 0123abc
            # date: 2026-11-01T10:00:00Z
            # api-version: 1
            #
            %s  react-rcp-1.4.0-sources.jar\r
            %s *react-rcp-1.4.0.jar
            deadbeef  short-hash.jar
            """.formatted(SOURCES_HASH, JAR_HASH);

    @Test
    void readsTheHeader() {

        final ChecksumManifest checksums = ChecksumManifest.parse(RELEASE);

        assertEquals("1.4.0", checksums.version());
        assertEquals(1, checksums.apiVersion());
        assertEquals("release", checksums.header().get("channel"));
        assertEquals("2026-11-01T10:00:00Z", checksums.header().get("date"));

    }

    @Test
    void readsBothSha256sumModesAndSkipsBrokenLines() {

        final ChecksumManifest checksums = ChecksumManifest.parse(RELEASE);

        assertEquals(
                Map.of(
                        "react-rcp-1.4.0-sources.jar",
                        SOURCES_HASH.toLowerCase(),
                        "react-rcp-1.4.0.jar",
                        JAR_HASH
                ),
                checksums.files()
        );

    }

    @Test
    void findsTheFirstJarWithThePrefix() {

        final Map.Entry<String, String> jar = ChecksumManifest.parse(RELEASE).jar("react-rcp-1.4.0.");

        assertEquals(Map.entry("react-rcp-1.4.0.jar", JAR_HASH), jar);
        assertEquals(Map.entry("react-rcp-1.4.0.jar", JAR_HASH), ChecksumManifest.parse(RELEASE).jar("other-"));

    }

    @Test
    void fallsBackToTheOnlyJar() {

        final ChecksumManifest checksums = ChecksumManifest.parse(JAR_HASH + "  hologram-addon-1.0.jar\n");

        assertEquals(Map.entry("hologram-addon-1.0.jar", JAR_HASH), checksums.jar("hologram"));
        assertEquals(Map.entry("hologram-addon-1.0.jar", JAR_HASH), checksums.jar("other"));

    }

    @Test
    void selectsOnlyTheRequestedNonBukkitPlatformArtifact() {

        final ChecksumManifest checksums = ChecksumManifest.parse("""
                %s  hologram-addon-bukkit-1.0.jar
                %s  hologram-addon-fabric-1.0.jar
                %s  hologram-addon-sponge-1.0.jar
                %s  hologram-addon-minestom-1.0.jar
                """.formatted(JAR_HASH, SOURCES_HASH, JAR_HASH, SOURCES_HASH));

        assertEquals(Map.entry("hologram-addon-fabric-1.0.jar", SOURCES_HASH.toLowerCase()), checksums.jar("hologram-addon", "fabric"));
        assertEquals(Map.entry("hologram-addon-sponge-1.0.jar", JAR_HASH), checksums.jar("hologram-addon", "sponge"));
        assertEquals(Map.entry("hologram-addon-minestom-1.0.jar", SOURCES_HASH.toLowerCase()), checksums.jar("hologram-addon", "minestom"));
        assertNull(checksums.jar("hologram-addon", "unknown"));

    }

    @Test
    void bukkitPlatformsPreferTheirArtifactThenUseTheLegacyJar() {

        final ChecksumManifest variants = ChecksumManifest.parse("""
                # version: 1.0
                %s  menu-addon-fabric-1.0.jar
                %s  menu-addon-bukkit-1.0.jar
                """.formatted(JAR_HASH, SOURCES_HASH));
        final ChecksumManifest legacy = ChecksumManifest.parse("# version: 1.0\n" + JAR_HASH + "  menu-addon-1.0.jar\n");

        assertEquals(Map.entry("menu-addon-bukkit-1.0.jar", SOURCES_HASH.toLowerCase()), variants.jar("menu-addon", "paper"));
        assertEquals(Map.entry("menu-addon-1.0.jar", JAR_HASH), legacy.jar("menu-addon", "folia"));

    }

    @Test
    void missingOrBrokenApiVersionIsZero() {

        assertEquals(0, ChecksumManifest.parse("# version: 1\n").apiVersion());
        assertEquals(0, ChecksumManifest.parse("# api-version: one\n").apiVersion());
        assertNull(ChecksumManifest.parse("").version());

    }

    @Test
    void skipsDotfilesAndParentTraversalNames() {

        final ChecksumManifest checksums = ChecksumManifest.parse("""
                %s  .hidden.jar
                %s  ..
                %s  ../escape.jar
                %s  valid-1.0.0.jar
                """.formatted(JAR_HASH, JAR_HASH, JAR_HASH, JAR_HASH));

        assertEquals(Map.of("valid-1.0.0.jar", JAR_HASH), checksums.files());

    }

}
