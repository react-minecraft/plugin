package com.g4vrk.react.inference.server;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Writes the key from /react connect into inference.yml, keeping its comments and layout. */
public final class InferenceKeyStore {

    private InferenceKeyStore() {
    }

    public static void save(final @NotNull Path file, final @NotNull String apiKey) throws IOException {
        if (!apiKey.matches("[A-Za-z0-9_.:-]+")) {
            throw new IOException("The server returned an invalid key");
        }
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        boolean inAuth = false;
        boolean written = false;
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")) {
                inAuth = line.startsWith("auth:");
            } else if (inAuth && line.startsWith("  value:")) {
                lines.set(i, "  value: " + apiKey);
                written = true;
            } else if (inAuth && line.startsWith("  enabled:")) {
                lines.set(i, "  enabled: true");
            }
        }
        if (!written) {
            throw new IOException("auth.value was not found in " + file.getFileName());
        }
        final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

}
