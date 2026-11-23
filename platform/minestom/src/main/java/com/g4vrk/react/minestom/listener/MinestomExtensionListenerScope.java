package com.g4vrk.react.minestom.listener;

import com.g4vrk.react.platform.listener.PlatformListenerScope;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks Minestom event nodes and listeners owned by one extension.
 */
final class MinestomExtensionListenerScope implements PlatformListenerScope {

    private final Logger logger;
    private final EventNode<Event> node;
    private final Set<EventNode<?>> nodes = new HashSet<>();
    private final Set<EventListener<?>> listeners = new HashSet<>();
    private boolean closed;

    MinestomExtensionListenerScope(
            final @NotNull Logger logger
    ) {

        this.logger = logger;
        this.node = EventNode.all("react-extension");
        MinecraftServer.getGlobalEventHandler().addChild(node);

    }

    @Override
    public synchronized void register(
            final @NotNull Object listener
    ) {

        if (closed) {
            throw new IllegalStateException("Listener scope is closed");
        }
        if (listener instanceof final EventNode<?> eventNode && nodes.add(eventNode)) {
            node.addChild(eventNode);
            return;
        }
        if (listener instanceof final EventListener<?> eventListener && listeners.add(eventListener)) {
            node.addListener(eventListener);
            return;
        }
        logger.debug("Ignoring unsupported Minestom listener type: {}", listener.getClass().getName());

    }

    @Override
    public synchronized void unregister(
            final @NotNull Object listener
    ) {

        if (listener instanceof final EventNode<?> eventNode && nodes.remove(eventNode)) {
            node.removeChild(eventNode);
        }
        if (listener instanceof final EventListener<?> eventListener && listeners.remove(eventListener)) {
            node.removeListener(eventListener);
        }

    }

    @Override
    public synchronized void close() {

        if (closed) {
            return;
        }
        closed = true;
        MinecraftServer.getGlobalEventHandler().removeChild(node);
        nodes.clear();
        listeners.clear();

    }

}
