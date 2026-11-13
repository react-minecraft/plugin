package com.g4vrk.react.storage.backend;

import com.g4vrk.react.extension.library.RuntimeLibraries;
import com.g4vrk.react.storage.backend.jdbc.JdbcStorageBackend;
import com.g4vrk.react.storage.backend.mongo.MongoStorageBackend;
import com.g4vrk.react.storage.config.DatabaseSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Locale;
import java.io.IOException;

public final class StorageBackendFactory {

    public @NotNull StorageBackend create(final @NotNull DatabaseSettings settings) {

        if (StorageBackendFactory.class.getClassLoader() instanceof RuntimeLibraries libraries) {
            try {
                libraries.loadGroup("database-" + settings.type().name().toLowerCase(Locale.ROOT));
            } catch (final InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while loading " + settings.type() + " libraries", failure);
            } catch (final IOException failure) {
                throw new IllegalStateException("Could not load " + settings.type() + " libraries", failure);
            }
        }
        return switch (settings.type()) {
            case MONGODB -> new MongoStorageBackend(Objects.requireNonNull(settings.mongo()));
            case H2, MYSQL, SQLITE -> new JdbcStorageBackend(settings.type(), Objects.requireNonNull(settings.jdbc()));
        };
    }
}
