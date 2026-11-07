package com.g4vrk.react.update;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The signed {@code checksums.txt} of a release.
 *
 * <p>A header of {@code # key: value} lines (version, channel, commit, date, api-version)
 * followed by {@code sha256sum} lines.</p>
 */
public record Checksums(@NotNull Map<String, String> header, @NotNull Map<String, String> files) {

    /**
     * Reads the file.
     *
     * @param text the file contents
     * @return the header and the files; unreadable lines are skipped
     */
    public static @NotNull Checksums parse(final @NotNull String text) {
        final Map<String, String> header = new HashMap<>();
        final Map<String, String> files = new LinkedHashMap<>();
        for (final String raw : text.split("\\R")) {
            final String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                final int colon = line.indexOf(':');
                if (colon > 1) {
                    header.put(line.substring(1, colon).strip().toLowerCase(Locale.ROOT), line.substring(colon + 1).strip());
                }
                continue;
            }
            final String[] parts = line.split("\\s+\\*?", 2);
            if (parts.length == 2 && parts[0].length() == 64) {
                files.put(parts[1].strip(), parts[0].toLowerCase(Locale.ROOT));
            }
        }
        return new Checksums(header, files);
    }

    /**
     * The version in the header.
     *
     * @return the version, or {@code null}
     */
    public @Nullable String version() {
        return header.get("version");
    }

    /**
     * The API version the build needs.
     *
     * @return the version, {@code 0} when the header has none
     */
    public int apiVersion() {
        try {
            return Integer.parseInt(header.getOrDefault("api-version", "0"));
        } catch (final NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * The jar whose name starts with {@code prefix}, or the only jar when none does.
     *
     * @param prefix the start of the jar name
     * @return the file name with its SHA-256, or {@code null}
     */
    public @Nullable Map.Entry<String, String> jar(final @NotNull String prefix) {
        Map.Entry<String, String> only = null;
        int jars = 0;
        for (final Map.Entry<String, String> entry : files.entrySet()) {
            if (!entry.getKey().endsWith(".jar")) {
                continue;
            }
            if (entry.getKey().startsWith(prefix)) {
                return entry;
            }
            only = entry;
            jars++;
        }
        return jars == 1 ? only : null;
    }

}
