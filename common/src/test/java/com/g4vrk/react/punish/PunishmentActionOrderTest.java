package com.g4vrk.react.punish;

import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import net.kyori.adventure.audience.Audience;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PunishmentActionOrderTest {

    @Test
    void startsCommonAndSpecialActionsInConfiguredOrder() {
        final List<String> called = new ArrayList<>();
        final SimpleActionRegistry<Audience> registry = new SimpleActionRegistry<>();
        registry.register("capture", (audience, argument) -> called.add(argument));
        final var steps = PunishmentManager.compileActions(
                List.of("[capture] first", "[log] second", "[capture] third", "[reset-vl]"),
                new SimpleActionParser<>(registry)
        );

        PunishmentManager.runActions(steps, Audience.empty(), UnaryOperator.identity(), called::add);

        assertEquals(List.of("first", "[log] second", "third", "[reset-vl]"), called);
    }

}
