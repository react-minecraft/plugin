package com.g4vrk.react.rcp.update;

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
        final Checksums checksums = Checksums.parse(RELEASE);

        assertEquals("1.4.0", checksums.version());
        assertEquals(1, checksums.apiVersion());
        assertEquals("release", checksums.header().get("channel"));
        assertEquals("2026-11-01T10:00:00Z", checksums.header().get("date"));
    }

    @Test
    void readsBothSha256sumModesAndSkipsBrokenLines() {
        final Checksums checksums = Checksums.parse(RELEASE);

        assertEquals(Map.of(
                "react-rcp-1.4.0-sources.jar", SOURCES_HASH.toLowerCase(),
                "react-rcp-1.4.0.jar", JAR_HASH), checksums.files());
    }

    @Test
    void findsTheFirstJarWithThePrefix() {
        final Map.Entry<String, String> jar = Checksums.parse(RELEASE).jar("react-rcp-1.4.0.");

        assertEquals(Map.entry("react-rcp-1.4.0.jar", JAR_HASH), jar);
        assertNull(Checksums.parse(RELEASE).jar("other-"));
    }

    @Test
    void missingOrBrokenApiVersionIsZero() {
        assertEquals(0, Checksums.parse("# version: 1\n").apiVersion());
        assertEquals(0, Checksums.parse("# api-version: one\n").apiVersion());
        assertNull(Checksums.parse("").version());
    }

}
