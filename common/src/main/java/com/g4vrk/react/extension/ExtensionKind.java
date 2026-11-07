package com.g4vrk.react.extension;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * The two kinds of jars the plugin loads, in load order: addons, then RCP providers.
 */
public enum ExtensionKind {

    ADDON("addon", "addons", "addons", "react-addon.yml"),
    RCP("RCP", ".rcp/providers", ".rcp", "rcp-metadata.yml");

    private final String displayName;
    private final String jars;
    private final String data;
    private final String metadataFile;

    ExtensionKind(
            final @NotNull String displayName,
            final @NotNull String jars,
            final @NotNull String data,
            final @NotNull String metadataFile
    ) {
        this.displayName = displayName;
        this.jars = jars;
        this.data = data;
        this.metadataFile = metadataFile;
    }

    /**
     * How log lines and messages name this kind.
     *
     * @return {@code RCP} or {@code addon}
     */
    public @NotNull String displayName() {
        return displayName;
    }

    /**
     * The folder the jars of this kind are read from.
     *
     * @param dataFolder the plugin folder
     * @return the jar folder
     */
    public @NotNull Path jars(final @NotNull Path dataFolder) {
        return dataFolder.resolve(jars);
    }

    /**
     * The folder the data folders of this kind live in.
     *
     * @param dataFolder the plugin folder
     * @return the data root
     */
    public @NotNull Path data(final @NotNull Path dataFolder) {
        return dataFolder.resolve(data);
    }

    /**
     * The metadata file inside a jar of this kind.
     *
     * @return the file name
     */
    public @NotNull String metadataFile() {
        return metadataFile;
    }

}
