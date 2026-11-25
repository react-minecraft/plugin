/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.punish;

import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler.ActionStep;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler.CompiledCatalog;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler.RuleSet;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler.SpecialKind;
import net.kyori.adventure.audience.Audience;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks punishment compilation, repeating {@code N;M} trigger math, and invalid-rule resilience.
 */
final class PunishmentRuleCompilerLockTest {

    @Test
    void compilesAtAndEveryRulesWithInvalidThresholdToleranceAndHighestPointTriggering() throws Exception {

        final String yaml = """
                punishments:
                  aim:
                    checks:
                      - Aim-AI
                    at:
                      5:
                        - "[alert] flagged <vl>"
                      0:
                        - "[log] invalid zero"
                      bad:
                        - "[log] invalid text"
                    every:
                      10:
                        - "[console] kick <player>"
                      "3;4":
                        - "[reset-vl]"
                  empty-group:
                    checks: []
                    at:
                      1:
                        - "[log] skipped"
                """;
        final ConfigurationNode root = YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .build()
                .load();

        final SimpleActionRegistry<Audience> registry = new SimpleActionRegistry<>();
        final CompiledCatalog catalog = PunishmentRuleCompiler.compile(root, new SimpleActionParser<>(registry), NOPLogger.NOP_LOGGER);

        assertEquals(1, catalog.rules().size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.apiView().clear());

        final RuleSet ruleSet = catalog.rules().get("aim-ai");
        assertEquals(3, ruleSet.rules().length);

        final PunishmentRuleCompiler.Rule atFive = ruleSet.rules()[0];
        assertEquals(5, atFive.trigger(4.9D, 5.0D));
        assertEquals(-1, atFive.trigger(5.0D, 6.0D));
        assertEquals(-1, atFive.trigger(6.0D, 4.0D));

        final PunishmentRuleCompiler.Rule everyTen = ruleSet.rules()[1];
        assertEquals(20, everyTen.trigger(9.0D, 24.0D));
        assertEquals(-1, everyTen.trigger(20.0D, 29.0D));

        final PunishmentRuleCompiler.Rule shifted = ruleSet.rules()[2];
        assertEquals(-1, shifted.trigger(1.0D, 2.5D));
        assertEquals(3, shifted.trigger(2.5D, 3.0D));
        assertEquals(11, shifted.trigger(6.9D, 11.5D));

        final List<ActionStep> steps = PunishmentRuleCompiler.compileActions(
                List.of("[alert] hi", "[log] trace", "[reset]", "[reset-vl]", "[cmd] say hi", "[console] kick p"),
                new SimpleActionParser<>(registry)
        );
        assertEquals(SpecialKind.ALERT, steps.get(0).kind());
        assertEquals("hi", steps.get(0).arguments());
        assertEquals(SpecialKind.LOG, steps.get(1).kind());
        assertEquals(SpecialKind.RESET_VL, steps.get(2).kind());
        assertEquals(SpecialKind.RESET_VL, steps.get(3).kind());
        assertEquals(SpecialKind.CONSOLE, steps.get(4).kind());
        assertEquals("say hi", steps.get(4).arguments());
        assertEquals(SpecialKind.CONSOLE, steps.get(5).kind());
        assertTrue(catalog.apiView().containsKey("aim-ai"));

    }

}
