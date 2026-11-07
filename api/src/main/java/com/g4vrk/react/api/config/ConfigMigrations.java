package com.g4vrk.react.api.config;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.List;

/**
 * The ordered migration chain of one config file.
 *
 * <p>Every file stores its version under {@value #VERSION_KEY}. A file without the key is
 * treated as version {@code 0}, the layout before versions existed. The first step turns
 * version {@code 0} into {@code 1}, the second {@code 1} into {@code 2}, and so on, so a new
 * layout only needs one more {@link Builder#step} call.</p>
 */
@ApiStatus.AvailableSince("2.0")
public final class ConfigMigrations {

    public static final String VERSION_KEY = "config-version";

    public static final int LEGACY_VERSION = 0;

    private static final ConfigMigrations NONE = builder().step(root -> {
    }).build();

    private final List<ConfigMigration> steps;
    private final int latest;

    private ConfigMigrations(final @NotNull List<ConfigMigration> steps, final int latest) {
        this.steps = steps;
        this.latest = latest;
    }

    /**
     * A chain for a file whose layout has not changed since versions were introduced.
     * Its only step stamps version {@code 1} on legacy files.
     */
    public static @NotNull ConfigMigrations none() {
        return NONE;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public int latest() {
        return latest;
    }

    public static int version(final @NotNull ConfigurationNode root) {
        return root.node(VERSION_KEY).getInt(LEGACY_VERSION);
    }

    /**
     * Runs every step the file has not seen yet and stamps the latest version.
     *
     * @return the version the file had before, or {@code -1} if it was already current
     */
    int apply(final @NotNull ConfigurationNode root) throws SerializationException {
        final int current = version(root);

        if (current > latest) {
            throw new SerializationException(root.node(VERSION_KEY), Integer.class,
                    "File version " + current + " is newer than this build supports (" + latest + ")");
        }
        if (current == latest) {
            return -1;
        }

        for (int version = current; version < latest; version++) {
            steps.get(version).migrate(root);
        }
        root.node(VERSION_KEY).set(latest);
        return current;
    }

    public static final class Builder {

        private final List<ConfigMigration> steps = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds the step that produces the next version. The first call produces version {@code 1}.
         */
        public @NotNull Builder step(final @NotNull ConfigMigration migration) {
            steps.add(migration);
            return this;
        }

        public @NotNull ConfigMigrations build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("A migration chain needs at least the step to version 1");
            }
            return new ConfigMigrations(List.copyOf(steps), steps.size());
        }

    }

}
