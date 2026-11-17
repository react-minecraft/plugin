package com.g4vrk.react.reboot.server;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapts the Bukkit and Paper plugin registries for in-place reloads on the main server thread.
 * Reflective handles are captured before the old plugin is disabled.
 */
@ApiStatus.Internal
public final class PluginReloadAccess {

    private final List<List<?>> pluginLists = new ArrayList<>();
    private final List<Map<?, ?>> nameLookups = new ArrayList<>();
    private final @Nullable Object dependencyTree;
    private final @Nullable Method dependencyTreeRemove;
    private final @Nullable Object pluginMeta;
    private final @Nullable Method syncCommands;
    private final boolean loadCallsOnLoad;

    public PluginReloadAccess(final @NotNull Plugin plugin) throws ReflectiveOperationException {

        final PluginManager manager = Bukkit.getPluginManager();
        collectStorage(manager);

        @Nullable Object tree = null;
        @Nullable Method remove = null;
        @Nullable Object meta = null;
        final @Nullable Object paperManager = readField(manager, "paperPluginManager");
        if (paperManager != null) {
            final @Nullable Object instanceManager = readField(paperManager, "instanceManager");
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
     * Removes the disabled plugin's commands, permissions and registry entries in server order.
     */
    public void forgetPlugin(
            final @NotNull PluginManager manager,
            final @NotNull Plugin plugin,
            final @NotNull ClassLoader oldLoader
    ) throws ReflectiveOperationException {

        unregisterCommands(plugin, oldLoader);
        for (final Permission permission : plugin.getDescription().getPermissions()) {
            manager.removePermission(permission);
        }
        for (final List<?> list : pluginLists) {
            list.remove(plugin);
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

    }

    /**
     * Loads the replacement plugin and invokes onLoad when the platform leaves it to the caller.
     */
    public @NotNull Plugin loadAndEnable(
            final @NotNull PluginManager manager,
            final @NotNull File jar
    ) throws InvalidPluginException, InvalidDescriptionException {

        final @Nullable Plugin loaded = manager.loadPlugin(jar);
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
        return loaded;

    }

    /**
     * Updates the command tree on platforms with a synchronization method.
     */
    public void syncCommands(final @NotNull Logger logger) {

        if (syncCommands == null) {
            return;
        }
        try {
            syncCommands.invoke(Bukkit.getServer());
        } catch (final Throwable failure) {
            logger.log(Level.WARNING, "Could not send the new commands to players", failure);
        }

    }

    private static void unregisterCommands(final @NotNull Plugin plugin, final @NotNull ClassLoader oldLoader) {

        final CommandMap commandMap = Bukkit.getServer().getCommandMap();
        final Iterator<Map.Entry<String, Command>> commands = commandMap.getKnownCommands().entrySet().iterator();
        while (commands.hasNext()) {
            final Command command = commands.next().getValue();
            final boolean owned = command instanceof PluginIdentifiableCommand identifiable ? identifiable.getPlugin() == plugin : command.getClass().getClassLoader() == oldLoader;
            if (owned) {
                commands.remove();
                command.unregister(commandMap);
            }
        }

    }

    private void collectStorage(final @NotNull Object owner) throws IllegalAccessException {

        if (readField(owner, "plugins") instanceof List<?> plugins) {
            pluginLists.add(plugins);
        }
        if (readField(owner, "lookupNames") instanceof Map<?, ?> lookupNames) {
            nameLookups.add(lookupNames);
        }

    }

    private static @Nullable Object readField(
            final @NotNull Object owner,
            final @NotNull String name
    ) throws IllegalAccessException {

        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (final NoSuchFieldException ignored) {
                /*
                 * Paper may inherit the registry from another implementation.
                 */
            }
        }
        return null;

    }

    private static @Nullable Method findRemove(final @NotNull Class<?> tree, final @NotNull Class<?> meta) {

        for (final Method method : tree.getMethods()) {
            if (method.getName().equals("remove") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].getSimpleName().equals("PluginMeta")
                    && method.getParameterTypes()[0].isAssignableFrom(meta)) {
                return method;
            }
        }
        return null;

    }

    private static @Nullable Method findMethod(final @NotNull Class<?> type, final @NotNull String name) {

        try {
            return type.getMethod(name);
        } catch (final NoSuchMethodException ignored) {
            return null;
        }

    }

}
