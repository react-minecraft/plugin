package com.g4vrk.react.update.config;

import com.g4vrk.react.extension.descriptor.ExtensionKind;
import com.g4vrk.react.update.UpdateChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies update channels selected for fresh component configuration files.
 */
final class UpdateConfigurationTest {

    @Test
    void usesStableForPluginAndNightlyForExtensions(@TempDir final Path directory) {

        final UpdateConfiguration configuration = new UpdateConfiguration(
                directory,
                directory.resolve("update"),
                "en",
                NOPLogger.NOP_LOGGER
        );

        assertEquals(UpdateChannel.RELEASE, configuration.component(null, "React").effectiveChannel());
        assertEquals(UpdateChannel.PRERELEASE,
                configuration.component(ExtensionKind.ADDON, "Hologram").effectiveChannel());
        assertEquals(UpdateChannel.PRERELEASE,
                configuration.component(ExtensionKind.RCP, "React").effectiveChannel());

    }

}
