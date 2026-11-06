package com.g4vrk.react.rcp.update;

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
record Checksums(@NotNull Map<String, String> header, @NotNull Map<String, String> files) {

    static @NotNull Checksums parse(final @NotNull String text) {
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

    @Nullable String version() {
        return header.get("version");
    }

    int apiVersion() {
        try {
            return Integer.parseInt(header.getOrDefault("api-version", "0"));
        } catch (final NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * The jar whose name starts with {@code prefix}, with its hash.
     */
    @Nullable Map.Entry<String, String> jar(final @NotNull String prefix) {
        for (final Map.Entry<String, String> entry : files.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getKey().endsWith(".jar")) {
                return entry;
            }
        }
        return null;
    }

}
