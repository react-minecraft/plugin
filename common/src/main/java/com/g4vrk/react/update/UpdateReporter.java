package com.g4vrk.react.update;

import com.g4vrk.react.api.event.plugin.UpdateDownloadedEvent;
import com.g4vrk.react.api.rcp.RCPAPI;
import com.g4vrk.react.update.client.ReleaseClient;
import com.g4vrk.react.update.component.UpdateTargetScanner.TargetComponent;
import com.g4vrk.react.update.config.UpdateConfig;
import com.g4vrk.react.update.stage.StagedUpdateStore;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Emits deduplicated console notices and compatibility warnings for downloaded or incompatible updates.
 */
final class UpdateReporter {

    private final Logger logger;
    private final Map<String, UpdateDownloadedEvent> downloaded;
    private final Supplier<UpdateConfig> settings;
    private final Map<String, String> warned = new ConcurrentHashMap<>();

    UpdateReporter(
            final @NotNull Logger logger,
            final @NotNull Map<String, UpdateDownloadedEvent> downloaded,
            final @NotNull Supplier<UpdateConfig> settings
    ) {

        this.logger = logger;
        this.downloaded = downloaded;
        this.settings = settings;

    }

    void announce(
            final @NotNull TargetComponent component,
            final @NotNull String version,
            final boolean reminder
    ) {

        final String downloadedKey = component.extension() == null
                ? "plugin"
                : StagedUpdateStore.extensionKey(component.extension(), component.name());
        if (reminder && !downloaded.containsKey(downloadedKey)) {
            return;
        }
        logger.info(
                "{} {} {} is downloaded, running {}. {}",
                reminder ? "Waiting to apply:" : "Update:",
                component.kind().name().toLowerCase(Locale.ROOT),
                component.name() + " " + version,
                component.version(),
                applyHint(component)
        );
        logger.info("Release notes and manual download: {}", releasesUrl(component));

    }

    void needsNewerPlugin(
            final @NotNull TargetComponent component,
            final @NotNull ReleaseClient.Release release
    ) {

        final String previous = warned.put(key(component.repository()), release.version());
        if (release.version().equals(previous)) {
            return;
        }
        logger.warn(
                "{} {} is released but needs api-version {}, this plugin provides {}. Update React first, then it follows: {}",
                component.name(),
                release.version(),
                release.checksums().apiVersion(),
                RCPAPI.VERSION,
                releasesUrl(component)
        );

    }

    private @NotNull String applyHint(
            final @NotNull TargetComponent component
    ) {

        if (component.extension() == null) {
            return "Restart the server to apply it.";
        }
        if (settings.get().autoReboot()) {
            return "Auto-reboot is on, it is applied right away.";
        }
        return "Run '/react reboot " + component.name() + "' to apply it, or set auto-reboot: true in update/settings.yml.";

    }

    private static @NotNull String releasesUrl(
            final @NotNull TargetComponent component
    ) {

        return "https://github.com/" + component.repository() + "/releases";

    }

    private static @NotNull String key(
            final @NotNull String repository
    ) {

        return repository.toLowerCase(Locale.ROOT);

    }

}
