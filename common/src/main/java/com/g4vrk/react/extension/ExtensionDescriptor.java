package com.g4vrk.react.extension;

import com.g4vrk.react.api.addon.meta.AddonMetadata;
import com.g4vrk.react.api.extension.GitHubSource;
import com.g4vrk.react.api.rcp.RCPAPI;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What the metadata file of an extension jar says.
 *
 * @param kind         whether the jar is an RCP or an addon
 * @param jar          the jar file
 * @param name         the extension name, safe to use as a folder name
 * @param version      the extension version
 * @param mainClass    the class the plugin creates
 * @param apiVersion   the API version the jar was built for
 * @param description  the addon description
 * @param authors      the addon authors
 * @param contributors the addon contributors
 * @param github       where updates come from
 */
public record ExtensionDescriptor(
        @NotNull ExtensionKind kind,
        @NotNull Path jar,
        @NotNull String name,
        @NotNull String version,
        @NotNull String mainClass,
        int apiVersion,
        @Nullable String description,
        @Nullable List<String> authors,
        @Nullable List<String> contributors,
        @Nullable GitHubSource github
) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}");

    /**
     * Reads the metadata file of a jar.
     *
     * @param kind the kind the folder holds
     * @param jar  the jar file
     * @return the descriptor
     * @throws IOException               when the jar cannot be read
     * @throws InvalidExtensionException when the metadata is missing or wrong
     */
    public static @NotNull ExtensionDescriptor read(
            final @NotNull ExtensionKind kind,
            final @NotNull Path jar
    ) throws IOException, InvalidExtensionException {
        final ConfigurationNode root;

        try (FileSystem fileSystem = FileSystems.newFileSystem(jar, (ClassLoader) null)) {
            final Path file = fileSystem.getPath(kind.metadataFile());
            if (!Files.isRegularFile(file)) {
                throw new InvalidExtensionException(jar.getFileName() + " has no " + kind.metadataFile());
            }
            root = YamlConfigurationLoader.builder().path(file).build().load();
        }

        final String name = required(jar, root, "name");
        if (!NAME.matcher(name).matches()) {
            throw new InvalidExtensionException(jar.getFileName() + " has an invalid name '" + name + "'");
        }

        final int apiVersion = root.node("api-version").getInt(0);
        if (apiVersion != RCPAPI.VERSION) {
            throw new InvalidExtensionException(
                    kind.displayName() + " '" + name + "' is built for api-version " + apiVersion
                            + ", this plugin provides " + RCPAPI.VERSION
            );
        }

        final ConfigurationNode github = root.node("github");
        final String repository = github.node("repository").getString();

        return new ExtensionDescriptor(
                kind,
                jar,
                name,
                required(jar, root, "version"),
                required(jar, root, "mainClass"),
                apiVersion,
                root.node("description").getString(),
                root.node("authors").virtual() ? null : root.node("authors").getList(String.class),
                root.node("contributors").virtual() ? null : root.node("contributors").getList(String.class),
                repository == null ? null : new GitHubSource(repository, github.node("updatable").getBoolean(true))
        );
    }

    private static @NotNull String required(
            final @NotNull Path jar,
            final @NotNull ConfigurationNode root,
            final @NotNull String key
    ) throws InvalidExtensionException {
        final String value = root.node(key).getString();
        if (value == null || value.isBlank()) {
            throw new InvalidExtensionException(jar.getFileName() + " has no '" + key + "' in its metadata");
        }
        return value;
    }

    /**
     * The metadata as an RCP sees it.
     *
     * @return the RCP metadata
     */
    public @NotNull RCPMetadata rcpMetadata() {
        return new RCPMetadata(name, version, mainClass, apiVersion, github);
    }

    /**
     * The metadata as an addon sees it.
     *
     * @return the addon metadata
     */
    public @NotNull AddonMetadata addonMetadata() {
        return new AddonMetadata(name, version, mainClass, apiVersion, description, authors, contributors, github);
    }

}
