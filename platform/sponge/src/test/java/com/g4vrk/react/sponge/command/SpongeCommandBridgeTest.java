package com.g4vrk.react.sponge.command;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import org.incendo.cloud.CommandManager;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.Logger;
import org.spongepowered.plugin.PluginContainer;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class SpongeCommandBridgeTest {

    @Test
    void supportsCloudRootDeletionForExtensionShutdown() {

        final SpongeCommandBridge bridge = new SpongeCommandBridge(
                proxy(PluginContainer.class),
                proxy(ReactPlatform.class),
                proxy(Logger.class),
                proxy(TaskScheduler.class)
        );
        final CommandManager<PlatformSender> manager = bridge.manager();

        assertDoesNotThrow(() -> manager.deleteRootCommand("missing"));

    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<?> type) {

        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> null
        );

    }

}
