package com.g4vrk.react.update.component;

import com.g4vrk.react.api.event.plugin.UpdateDownloadedEvent;
import com.g4vrk.react.api.extension.meta.GitHubSource;
import com.g4vrk.react.extension.descriptor.ExtensionDescriptor;
import com.g4vrk.react.extension.descriptor.ExtensionKind;
import com.g4vrk.react.extension.descriptor.InvalidExtensionException;
import com.g4vrk.react.update.client.ReleaseClient;
import com.g4vrk.react.update.stage.StagedUpdateStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers installed updatable components (plugin, RCPs, and addons) and their release metadata.
 */
public final class UpdateTargetScanner {

    private static final String PLUGIN_REPOSITORY = "react-minecraft/plugin";
    private static final String REACT_RCP_PREFIX = "react-inference-rcp";

    private UpdateTargetScanner() {

        throw new UnsupportedOperationException("Utility class");

    }

    /**
     * One updatable component resolved on disk.
     */
    public record TargetComponent(
            @NotNull UpdateDownloadedEvent.Kind kind,
            @NotNull String name,
            @NotNull String repository,
            @NotNull String prefix,
            @NotNull Path current,
            @NotNull Path target,
            @Nullable ExtensionKind extension,
            @NotNull String version
    ) {

    }

    public static @NotNull List<TargetComponent> collect(
            final @NotNull Path dataFolder,
            final @Nullable Path pluginJar,
            final @NotNull Path pluginUpdateFolder,
            final @NotNull String pluginPrefix,
            final @NotNull String pluginVersion,
            final @NotNull StagedUpdateStore staging
    ) {

        final List<TargetComponent> components = new ArrayList<>();
        if (pluginJar != null) {
            components.add(new TargetComponent(
                    UpdateDownloadedEvent.Kind.PLUGIN,
                    "React",
                    PLUGIN_REPOSITORY,
                    pluginPrefix,
                    pluginJar,
                    pluginUpdateFolder.resolve(pluginJar.getFileName()),
                    null,
                    pluginVersion
            ));
        }
        for (final ExtensionKind kind : ExtensionKind.values()) {
            for (final ExtensionDescriptor descriptor : scan(dataFolder, kind)) {
                final GitHubSource github = source(descriptor);
                if (github == null || !github.updatable()) {
                    continue;
                }
                components.add(new TargetComponent(
                        kind == ExtensionKind.RCP ? UpdateDownloadedEvent.Kind.RCP : UpdateDownloadedEvent.Kind.ADDON,
                        descriptor.name(),
                        github.repository(),
                        ReleaseClient.REACT_RCP.equalsIgnoreCase(github.repository())
                                ? REACT_RCP_PREFIX
                                : repositoryPrefix(github.repository()),
                        descriptor.jar(),
                        staging.stagedFolder(kind, descriptor.name()),
                        kind,
                        descriptor.version()
                ));
            }
        }
        return components;

    }

    public static @NotNull List<ExtensionDescriptor> scan(
            final @NotNull Path dataFolder,
            final @NotNull ExtensionKind kind
    ) {

        final List<ExtensionDescriptor> descriptors = new ArrayList<>();
        for (final Path jar : StagedUpdateStore.jars(kind.jars(dataFolder))) {
            try {
                descriptors.add(ExtensionDescriptor.read(kind, jar));
            } catch (final IOException | InvalidExtensionException | RuntimeException ignored) {
                /*
                 * The extension manager reports unreadable jars when it loads them.
                 */
            }
        }
        return descriptors;

    }

    public static boolean isReactRcp(
            final @NotNull ExtensionDescriptor descriptor
    ) {

        final GitHubSource source = source(descriptor);
        return source != null && ReleaseClient.REACT_RCP.equalsIgnoreCase(source.repository());

    }

    public static @Nullable GitHubSource source(
            final @NotNull ExtensionDescriptor descriptor
    ) {

        final GitHubSource declared = descriptor.github();
        if (declared != null) {
            return declared;
        }
        if (descriptor.kind() == ExtensionKind.RCP && descriptor.name().equalsIgnoreCase("React")
                && descriptor.mainClass().equals("com.g4vrk.react.inference.ReactInferenceRCP")) {
            return new GitHubSource(ReleaseClient.REACT_RCP, true);
        }
        return null;

    }

    public static @NotNull String repositoryPrefix(
            final @NotNull String repository
    ) {

        return repository.substring(repository.indexOf('/') + 1);

    }

}
