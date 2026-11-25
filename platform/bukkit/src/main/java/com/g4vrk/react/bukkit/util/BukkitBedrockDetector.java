package com.g4vrk.react.bukkit.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflectively detects Bedrock players via Floodgate or Geyser on Bukkit-based servers.
 */
public final class BukkitBedrockDetector {

    private static final DetectorSlot FLOODGATE = new DetectorSlot(
            "org.geysermc.floodgate.api.FloodgateApi",
            "getInstance",
            "isFloodgatePlayer"
    );
    private static final DetectorSlot GEYSER = new DetectorSlot(
            "org.geysermc.api.Geyser",
            "api",
            "isBedrockPlayer"
    );

    private BukkitBedrockDetector() {

        throw new UnsupportedOperationException("Utility class");

    }

    public static boolean isBedrockPlayer(
            final @NotNull UUID uuid
    ) {

        final PlayerDetector floodgate = FLOODGATE.resolve();
        if (floodgate != null && floodgate.isBedrockPlayer(uuid)) {
            return true;
        }

        final PlayerDetector geyser = GEYSER.resolve();
        return geyser != null && geyser.isBedrockPlayer(uuid);

    }

    private static final class DetectorSlot {

        private final String apiClassName;
        private final String factoryMethodName;
        private final String detectorMethodName;
        private volatile @Nullable PlayerDetector detector;
        private volatile int scannedPluginCount = -1;

        private DetectorSlot(
                final @NotNull String apiClassName,
                final @NotNull String factoryMethodName,
                final @NotNull String detectorMethodName
        ) {

            this.apiClassName = apiClassName;
            this.factoryMethodName = factoryMethodName;
            this.detectorMethodName = detectorMethodName;

        }

        private @Nullable PlayerDetector resolve() {

            PlayerDetector resolved = detector;
            if (resolved != null) {
                return resolved;
            }
            final Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
            if (plugins.length == scannedPluginCount) {
                return null;
            }
            synchronized (this) {
                resolved = detector;
                if (resolved != null || plugins.length == scannedPluginCount) {
                    return resolved;
                }
                resolved = findDetector(plugins, apiClassName, factoryMethodName, detectorMethodName);
                if (resolved != null) {
                    detector = resolved;
                }
                scannedPluginCount = plugins.length;
                return resolved;
            }

        }

    }

    private static @Nullable PlayerDetector findDetector(
            final @NotNull Plugin[] plugins,
            final @NotNull String apiClassName,
            final @NotNull String factoryMethodName,
            final @NotNull String detectorMethodName
    ) {

        for (final Plugin plugin : plugins) {
            final PlayerDetector detector = findDetector(
                    plugin.getClass().getClassLoader(),
                    apiClassName,
                    factoryMethodName,
                    detectorMethodName
            );
            if (detector != null) {
                return detector;
            }
        }

        return findDetector(
                BukkitBedrockDetector.class.getClassLoader(),
                apiClassName,
                factoryMethodName,
                detectorMethodName
        );

    }

    private static @Nullable PlayerDetector findDetector(
            final @NotNull ClassLoader classLoader,
            final @NotNull String apiClassName,
            final @NotNull String factoryMethodName,
            final @NotNull String detectorMethodName
    ) {

        try {
            final Class<?> apiClass = Class.forName(apiClassName, false, classLoader);
            final Method factory = apiClass.getMethod(factoryMethodName);
            final Object instance = factory.invoke(null);
            if (instance == null) {
                return null;
            }
            final Method detector = apiClass.getMethod(detectorMethodName, UUID.class);
            return new PlayerDetector(instance, detector);
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }

    }

    private record PlayerDetector(
            @NotNull Object instance,
            @NotNull Method method
    ) {

        private boolean isBedrockPlayer(
                final @NotNull UUID uuid
        ) {

            try {
                return Boolean.TRUE.equals(method.invoke(instance, uuid));
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }

        }

    }

}
