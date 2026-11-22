package com.g4vrk.react.bukkit.reboot;

import com.g4vrk.react.bukkit.reboot.jar.PluginJar;
import com.g4vrk.react.bukkit.reboot.server.PluginReloadBridge;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Replaces the running plugin with the JAR currently in the plugins directory.
 *
 * <p>Prepare the task while the old plugin class loader is open and execute it on the main
 * server thread after command dispatch. The task and its prepared server access can then finish
 * using only already-loaded classes and the JDK and Bukkit APIs.
 */
@ApiStatus.Internal
public final class PluginReboot implements Runnable {

    private final Plugin plugin;
    private final CommandSender sender;
    private final File jar;
    private final String newVersion;
    private final MessageSet messages;
    private final Logger logger;
    private final PluginReloadBridge access;

    private PluginReboot(
            final @NotNull Plugin plugin,
            final @NotNull CommandSender sender,
            final @NotNull File jar,
            final @NotNull String newVersion,
            final @NotNull MessageSet messages
    ) throws ReflectiveOperationException {

        this.plugin = plugin;
        this.sender = sender;
        this.jar = jar;
        this.newVersion = newVersion;
        this.messages = messages;
        this.logger = Bukkit.getLogger();
        this.access = new PluginReloadBridge(plugin);

    }

    /**
     * Checks whether the server and replacement JAR support rebooting this plugin.
     *
     * @param plugin the enabled plugin to replace
     * @param sender the reboot command sender
     * @param messages preformatted status messages
     * @return the prepared task
     * @throws IllegalStateException when the reboot cannot start
     */
    public static @NotNull PluginReboot prepare(
            final @NotNull Plugin plugin,
            final @NotNull CommandSender sender,
            final @NotNull MessageSet messages
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
        final String version = PluginJar.version(jar);
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
            access.forgetPlugin(manager, plugin, oldLoader);
        } catch (final Throwable failure) {
            fail("Could not unload " + name, failure);
            return;
        }
        tell(messages.unloaded());

        final Plugin loaded;
        try {
            loaded = access.loadAndEnable(manager, jar);
        } catch (final Throwable failure) {
            fail("Could not load " + jar.getName(), failure);
            return;
        }

        access.syncCommands(logger);
        final String loadedVersion = loaded.getDescription().getVersion();
        logger.info("[" + name + "] Rebooted " + name + " " + oldVersion + " -> " + loadedVersion);
        tell(messages.loaded().replace("<old>", oldVersion).replace("<new>", loadedVersion));

    }

    private void fail(final @NotNull String reason, final @NotNull Throwable failure) {

        logger.log(Level.SEVERE, "[" + plugin.getName() + "] " + reason, failure);
        tell(messages.failed().replace("<reason>", reason + ": " + failure));

    }

    private void tell(final @NotNull String message) {

        try {
            sender.sendMessage(message);
        } catch (final Throwable ignored) {
            /*
             * The sender may have left during the reboot.
             */
        }

    }

    /**
     * Finds the unique JAR with the plugin's name in its metadata.
     *
     * @param plugin the plugin whose JAR to find
     * @return the matching JAR
     * @throws IllegalStateException when there is no matching JAR or several match
     */
    public static @NotNull File findJar(final @NotNull Plugin plugin) {

        return PluginJar.find(plugin);

    }

    private static boolean isFolia() {

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }

    }

    /**
     * Holds legacy strings that can be sent after the plugin has been unloaded.
     */
    @ApiStatus.Internal
    public record MessageSet(
            @NotNull String unloaded,
            @NotNull String loaded,
            @NotNull String failed
    ) {
    }

}
