package com.g4vrk.react.reboot.jar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Locates and validates the Bukkit plugin artifact used for self-updates and reboots.
 */
@ApiStatus.Internal
public final class PluginJar {

    private PluginJar() {

    }

    /**
     * Finds the unique plugin JAR by its metadata name in the plugins directory.
     *
     * @param plugin the plugin whose JAR to find
     * @return the matching JAR
     */
    public static @NotNull File find(final @NotNull Plugin plugin) {

        /*
         * Paper may load a remapped copy, so scan the real plugins directory instead of
         * relying on the code source.
         */
        final @Nullable File folder = plugin.getDataFolder().getAbsoluteFile().getParentFile();
        final File[] files = folder == null ? null : folder.listFiles((dir, file) -> file.endsWith(".jar"));
        if (files == null) {
            throw new IllegalStateException("Cannot list the plugins folder " + folder);
        }

        final List<File> candidates = new ArrayList<>();
        for (final File file : files) {
            if (hasPluginName(file, plugin.getName())) {
                candidates.add(file);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No " + plugin.getName() + " jar in " + folder.getName() + "/");
        }
        if (candidates.size() > 1) {
            final StringBuilder names = new StringBuilder();
            for (final File file : candidates) {
                names.append(names.isEmpty() ? "" : ", ").append(file.getName());
            }
            throw new IllegalStateException("Several " + plugin.getName() + " jars in " + folder.getName()
                    + "/: " + names + "; keep one");
        }
        return candidates.get(0);

    }

    /**
     * Reads the version and verifies that the Bukkit entry point is present.
     *
     * @param jar the replacement plugin JAR
     * @return the version from the JAR's descriptor
     * @throws IllegalStateException if the descriptor or entry point cannot be read
     */
    public static @NotNull String version(final @NotNull File jar) {

        try (final JarFile file = new JarFile(jar)) {
            final PluginDescriptionFile description = readDescription(file);
            final String mainClass = description.getMain().replace('.', '/') + ".class";
            if (file.getJarEntry(mainClass) == null) {
                throw new IllegalStateException(jar.getName() + " has no main class " + description.getMain());
            }
            return description.getVersion();
        } catch (final IllegalStateException ex) {
            throw ex;
        } catch (final IOException | InvalidDescriptionException | RuntimeException ex) {
            throw new IllegalStateException("Cannot read " + jar.getName() + ": " + ex.getMessage(), ex);
        }

    }

    private static boolean hasPluginName(final @NotNull File file, final @NotNull String name) {

        try (final JarFile jar = new JarFile(file)) {
            return name.equals(readDescription(jar).getName());
        } catch (final IOException | InvalidDescriptionException | RuntimeException ignored) {
            return false;
        }

    }

    private static @NotNull PluginDescriptionFile readDescription(final @NotNull JarFile jar)
            throws IOException, InvalidDescriptionException {

        final @Nullable JarEntry entry = jar.getJarEntry("plugin.yml");
        if (entry == null) {
            throw new IllegalStateException(new File(jar.getName()).getName() + " has no plugin.yml");
        }
        try (final InputStream input = jar.getInputStream(entry)) {
            return new PluginDescriptionFile(input);
        }

    }

}
