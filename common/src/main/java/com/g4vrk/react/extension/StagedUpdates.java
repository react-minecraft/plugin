package com.g4vrk.react.extension;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Downloaded jars that wait for their extension to be loaded again.
 */
@FunctionalInterface
public interface StagedUpdates {

    /**
     * Nothing is ever staged.
     */
    StagedUpdates NONE = (kind, name, jar) -> jar;

    /**
     * Puts the staged jar of an extension in place of its current jar, if there is one.
     *
     * @param kind the extension kind
     * @param name the extension name
     * @param jar  the jar the extension was loaded from; it is not open any more
     * @return the jar to load now
     */
    @NotNull Path apply(final @NotNull ExtensionKind kind, final @NotNull String name, final @NotNull Path jar);

}
