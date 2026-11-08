package com.g4vrk.react.api.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Answers the value of one MiniMessage tag.
 *
 * <p>The value crosses to the text library as a plain string, which is what lets the plugin run on
 * every server version: below 1.18.2 there is no MiniMessage in the server, so the library carries
 * its own copy under a relocated package, and a resolver of the plugin's Adventure would be a
 * different type inside it.</p>
 *
 * <p>A mapper answers only the tags it owns: {@link #has(String)} keeps {@code <red>} and every
 * other standard tag out of the way.</p>
 */
@FunctionalInterface
public interface TagMapper {

    /**
     * Gives the markup of one tag.
     *
     * @param name the tag name, for example {@code react_vl}
     * @param args what followed the name, joined with {@code :}, empty when nothing did
     * @return the markup the tag stands for, or {@code null} to leave the tag as it is
     */
    @Nullable String map(final @NotNull String name, final @NotNull String args);

    /**
     * Tells whether this mapper owns a tag.
     *
     * @param name the tag name
     * @return {@code true} when {@link #map(String, String)} can answer this tag
     */
    default boolean has(final @NotNull String name) {
        return true;
    }

}
