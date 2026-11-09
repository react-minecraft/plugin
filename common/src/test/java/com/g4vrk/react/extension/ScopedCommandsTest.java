package com.g4vrk.react.extension;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedCommandsTest {

    private final TestManager manager = new TestManager();

    @Test
    void removesOnlyOwnSubcommandsUnderSharedRoot() {
        manager.command(manager.commandBuilder("react", "ac").literal("reload").handler(context -> { }));
        final ScopedCommands rcp = new ScopedCommands(manager, NOPLogger.NOP_LOGGER, "rcp");
        final ScopedCommands addon = new ScopedCommands(manager, NOPLogger.NOP_LOGGER, "addon");

        rcp.register("react", rcp.command("react").literal("link").handler(context -> { }));
        addon.register("react", addon.command("react").literal("punish").handler(context -> { }));
        rcp.close();

        assertEquals(List.of("punish", "reload"), literals());
        assertTrue(manager.commandTree().getNamedNode("react").component().aliases().contains("ac"));
        assertFalse(rcp.unregister("react"));
    }

    @Test
    void removesOwnRoot() {
        final ScopedCommands addon = new ScopedCommands(manager, NOPLogger.NOP_LOGGER, "addon");
        addon.register("punish", addon.command("punish").handler(context -> { }));

        assertTrue(addon.unregister("punish"));
        assertFalse(manager.rootCommands().contains("punish"));
    }

    @Test
    void rejectsMismatchedLabelAndClosedScope() {
        final ScopedCommands addon = new ScopedCommands(manager, NOPLogger.NOP_LOGGER, "addon");
        assertThrows(IllegalArgumentException.class,
                () -> addon.register("other", addon.command("punish").handler(context -> { })));
        addon.close();
        assertThrows(IllegalStateException.class,
                () -> addon.register("punish", addon.command("punish").handler(context -> { })));
    }

    private List<String> literals() {
        return manager.commands().stream()
                .filter(command -> command.rootComponent().name().equals("react"))
                .map(command -> command.components().get(1).name())
                .sorted()
                .toList();
    }

    private static final class TestManager extends CommandManager<CommandSender> {
        TestManager() {
            super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
            registerCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);
        }

        @Override
        public boolean hasPermission(final CommandSender sender, final String permission) {
            return true;
        }
    }
}
