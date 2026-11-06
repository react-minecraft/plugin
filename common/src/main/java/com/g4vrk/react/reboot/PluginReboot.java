package com.g4vrk.react.reboot;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unloads this plugin and loads it again from the jar now in the plugins folder, without
 * restarting the server. Addons are read again by the new instance.
 *
 * <p>Disabling the plugin closes its class loader, so {@link #run()} may only touch JDK and
 * Bukkit types and this class itself after that point: every reflective handle it needs is
 * looked up in {@link #prepare}, while the old class loader is still open.
 */
public final class PluginReboot implements Runnable {

    private final Plugin plugin;
    private final CommandSender sender;
    private final File jar;
    private final String newVersion;
    private final Messages messages;
    private final Logger logger;

    // Paper keeps loaded plugins in PaperPluginInstanceManager; plain Spigot in SimplePluginManager.
    private final List<Object> pluginLists = new ArrayList<>();
    private final List<Map<?, ?>> nameLookups = new ArrayList<>();
    private final @Nullable Object dependencyTree;
    private final @Nullable Method dependencyTreeRemove;
    private final @Nullable Object pluginMeta;
    private final @Nullable Method syncCommands;
    // Paper calls onLoad inside loadPlugin; Spigot leaves it to the caller.
    private final boolean loadCallsOnLoad;

    /** Sender-facing texts, built before the class loader closes. */
    public record Messages(String unloaded, String loaded, String failed) {
    }

    private PluginReboot(
            final Plugin plugin,
            final CommandSender sender,
            final File jar,
            final String newVersion,
            final Messages messages
    ) throws ReflectiveOperationException {
        this.plugin = plugin;
        this.sender = sender;
        this.jar = jar;
        this.newVersion = newVersion;
        this.messages = messages;
        this.logger = Bukkit.getLogger();

        final PluginManager manager = Bukkit.getPluginManager();
        collectStorage(manager);
        Object tree = null;
        Method remove = null;
        Object meta = null;
        final Object paperManager = readField(manager, "paperPluginManager");
        if (paperManager != null) {
            final Object instanceManager = readField(paperManager, "instanceManager");
            if (instanceManager == null) {
                throw new IllegalStateException("Unsupported Paper plugin manager: no instanceManager");
            }
            collectStorage(instanceManager);
            tree = readField(instanceManager, "dependencyTree");
            if (tree != null) {
                meta = plugin.getClass().getMethod("getPluginMeta").invoke(plugin);
                remove = findRemove(tree.getClass(), meta.getClass());
                if (remove == null) {
                    throw new IllegalStateException("Unsupported Paper dependency tree: no remove(PluginMeta)");
                }
            }
        }
        if (pluginLists.isEmpty() || nameLookups.isEmpty()) {
            throw new IllegalStateException("Unsupported server: the loaded plugin list was not found");
        }
        this.dependencyTree = tree;
        this.dependencyTreeRemove = remove;
        this.pluginMeta = meta;
        this.syncCommands = findMethod(Bukkit.getServer().getClass(), "syncCommands");
        this.loadCallsOnLoad = paperManager != null;
    }

    /**
     * Checks everything that can be checked while the plugin still runs. Throws
     * {@link IllegalStateException} with a reason to show the sender when the reboot cannot start.
     */
    public static @NotNull PluginReboot prepare(
            final @NotNull Plugin plugin,
            final @NotNull CommandSender sender,
            final @NotNull Messages messages
    ) {
        if (isFolia()) {
            throw new IllegalStateException("Folia cannot unload plugins, restart the server");
        }
        for (final Plugin other : Bukkit.getPluginManager().getPlugins()) {
            if (other != plugin && other.isEnabled() && other.getDescription().getDepend().contains(plugin.getName())) {
                throw new IllegalStateException(other.getName() + " requires " + plugin.getName()
                        + " (depend), restart the server instead");
            }
        }
        final File jar = findJar(plugin);
        final String version;
        try (JarFile file = new JarFile(jar)) {
            final PluginDescriptionFile description = readDescription(file);
            final String mainClass = description.getMain().replace('.', '/') + ".class";
            if (file.getJarEntry(mainClass) == null) {
                throw new IllegalStateException(jar.getName() + " has no main class " + description.getMain());
            }
            version = description.getVersion();
        } catch (final IllegalStateException ex) {
            throw ex;
        } catch (final Exception ex) {
            throw new IllegalStateException("Cannot read " + jar.getName() + ": " + ex.getMessage(), ex);
        }
        try {
            return new PluginReboot(plugin, sender, jar, version, messages);
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            throw new IllegalStateException("This server does not support unloading plugins: " + ex.getMessage(), ex);
        }
    }

    public @NotNull File jar() {
        return jar;
    }

    public @NotNull String newVersion() {
        return newVersion;
    }

    @Override
    public void run() {
        final String name = plugin.getName();
        final String oldVersion = plugin.getDescription().getVersion();
        final ClassLoader oldLoader = plugin.getClass().getClassLoader();
        final PluginManager manager = Bukkit.getPluginManager();
        logger.info("[" + name + "] Rebooting " + name + " " + oldVersion + " from " + jar.getName());

        try {
            manager.disablePlugin(plugin);
            unregisterCommands(oldLoader);
            for (final Permission permission : plugin.getDescription().getPermissions()) {
                manager.removePermission(permission);
            }
            for (final Object list : pluginLists) {
                ((List<?>) list).remove(plugin);
            }
            for (final Map<?, ?> lookup : nameLookups) {
                final Iterator<?> values = lookup.values().iterator();
                while (values.hasNext()) {
                    if (values.next() == plugin) {
                        values.remove();
                    }
                }
            }
            if (dependencyTree != null && dependencyTreeRemove != null) {
                dependencyTreeRemove.invoke(dependencyTree, pluginMeta);
            }
        } catch (final Throwable failure) {
            fail("Could not unload " + name, failure);
            return;
        }
        tell(messages.unloaded());

        final Plugin loaded;
        try {
            loaded = manager.loadPlugin(jar);
            if (loaded == null) {
                throw new IllegalStateException("the server returned no plugin");
            }
            if (!loadCallsOnLoad) {
                loaded.onLoad();
            }
            manager.enablePlugin(loaded);
            if (!loaded.isEnabled()) {
                throw new IllegalStateException("the plugin did not enable, see the log above");
            }
        } catch (final Throwable failure) {
            fail("Could not load " + jar.getName(), failure);
            return;
        }
        syncCommands();
        logger.info("[" + name + "] Rebooted " + name + " " + oldVersion + " -> " + loaded.getDescription().getVersion());
        tell(messages.loaded() + " " + oldVersion + " -> " + loaded.getDescription().getVersion());
    }

    private void unregisterCommands(final ClassLoader oldLoader) {
        final CommandMap commandMap = Bukkit.getServer().getCommandMap();
        final Iterator<Map.Entry<String, Command>> commands = commandMap.getKnownCommands().entrySet().iterator();
        while (commands.hasNext()) {
            final Command command = commands.next().getValue();
            final boolean owned = command instanceof PluginIdentifiableCommand identifiable
                    ? identifiable.getPlugin() == plugin
                    : command.getClass().getClassLoader() == oldLoader;
            if (owned) {
                commands.remove();
                command.unregister(commandMap);
            }
        }
    }

    private void syncCommands() {
        if (syncCommands == null) {
            return;
        }
        try {
            syncCommands.invoke(Bukkit.getServer());
        } catch (final Throwable failure) {
            logger.log(Level.WARNING, "Could not send the new commands to players", failure);
        }
    }

    private void fail(final String reason, final Throwable failure) {
        logger.log(Level.SEVERE, "[" + plugin.getName() + "] " + reason, failure);
        tell(messages.failed() + " " + reason + ": " + failure);
    }

    private void tell(final String message) {
        try {
            sender.sendMessage(message);
        } catch (final Throwable ignored) {
            // The sender may have left during the reboot.
        }
    }

    private void collectStorage(final Object owner) throws IllegalAccessException {
        final Object plugins = readField(owner, "plugins");
        if (plugins instanceof List<?>) {
            pluginLists.add(plugins);
        }
        final Object lookupNames = readField(owner, "lookupNames");
        if (lookupNames instanceof Map<?, ?> map) {
            nameLookups.add(map);
        }
    }

    private static @NotNull File findJar(final Plugin plugin) {
        // The data folder is plugins/<name>. The class's code source is no guide: Paper loads
        // remapped copies from plugins/.paper-remapped/.
        final File folder = plugin.getDataFolder().getAbsoluteFile().getParentFile();
        final File[] files = folder == null ? null : folder.listFiles((dir, file) -> file.endsWith(".jar"));
        if (files == null) {
            throw new IllegalStateException("Cannot list the plugins folder " + folder);
        }
        final List<File> candidates = new ArrayList<>();
        for (final File file : files) {
            if (isPlugin(file, plugin.getName())) {
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

    private static boolean isPlugin(final File file, final String name) {
        try (JarFile jar = new JarFile(file)) {
            return name.equals(readDescription(jar).getName());
        } catch (final Exception ignored) {
            // Not a Bukkit plugin, or a jar another plugin keeps open in a broken state.
            return false;
        }
    }

    private static PluginDescriptionFile readDescription(final JarFile jar) throws Exception {
        final JarEntry entry = jar.getJarEntry("plugin.yml");
        if (entry == null) {
            throw new IllegalStateException(new File(jar.getName()).getName() + " has no plugin.yml");
        }
        try (InputStream input = jar.getInputStream(entry)) {
            return new PluginDescriptionFile(input);
        }
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }

    private static @Nullable Object readField(final Object owner, final String name) throws IllegalAccessException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (final NoSuchFieldException ignored) {
                // Look in the superclass.
            }
        }
        return null;
    }

    private static @Nullable Method findRemove(final Class<?> tree, final Class<?> meta) {
        for (final Method method : tree.getMethods()) {
            if (method.getName().equals("remove") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].getSimpleName().equals("PluginMeta")
                    && method.getParameterTypes()[0].isAssignableFrom(meta)) {
                return method;
            }
        }
        return null;
    }

    private static @Nullable Method findMethod(final Class<?> type, final String name) {
        try {
            return type.getMethod(name);
        } catch (final NoSuchMethodException ignored) {
            return null;
        }
    }
}
