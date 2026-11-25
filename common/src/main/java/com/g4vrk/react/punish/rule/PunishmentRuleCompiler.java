package com.g4vrk.react.punish.rule;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.punish.PunishmentRule;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code punishments.yml} rule groups and compiles action sequences once on config reload.
 */
public final class PunishmentRuleCompiler {

    private static final char PERIOD_SEPARATOR = ';';

    private PunishmentRuleCompiler() {

        throw new UnsupportedOperationException("Utility class");

    }

    /**
     * Compiled per-check rule sets paired with their read-only API exposure map.
     */
    public record CompiledCatalog(
            @NotNull Map<String, RuleSet> rules,
            @NotNull Map<String, List<PunishmentRule>> apiView
    ) {

    }

    /**
     * All active punishment rules attached to one check identifier.
     */
    public record RuleSet(
            Rule @NotNull [] rules,
            @NotNull List<PunishmentRule> api
    ) {

        public static @NotNull RuleSet of(
                final @NotNull List<Rule> rules
        ) {

            final Rule[] array = rules.toArray(new Rule[0]);
            final List<PunishmentRule> api = new ObjectArrayList<>(array.length);
            for (final Rule rule : array) {
                api.add(new PunishmentRule(rule.start(), rule.period(), rule.lines()));
            }
            return new RuleSet(array, Collections.unmodifiableList(api));

        }

    }

    /**
     * One compiled threshold or repeating punishment rule.
     */
    public record Rule(
            int start,
            int period,
            @NotNull List<String> lines,
            @NotNull List<ActionStep> actions
    ) {

        public int trigger(
                final double oldVl,
                final double newVl
        ) {

            if (newVl < start) {
                return -1;
            }
            if (period <= 0) {
                return oldVl < start ? start : -1;
            }
            final long point = start + (long) Math.floor((newVl - start) / period) * period;
            return oldVl < point ? (int) point : -1;

        }

    }

    /**
     * Built-in punishment special action tags handled by React directly.
     */
    public enum SpecialKind {

        NONE,
        ALERT,
        LOG,
        RESET_VL,
        CONSOLE

    }

    /**
     * A contiguous action block or special punishment step in configured execution order.
     */
    public record ActionStep(
            @Nullable String special,
            @NotNull SpecialKind kind,
            @NotNull String arguments,
            @Nullable ExecutableActionList<? super Audience> actions
    ) {

        public ActionStep(
                final @Nullable String special,
                final @Nullable ExecutableActionList<? super Audience> actions
        ) {

            this(
                    special,
                    special == null ? SpecialKind.NONE : specialKind(special),
                    special == null ? "" : specialArguments(special),
                    actions
            );

        }

    }

    public static @NotNull CompiledCatalog compile(
            final @NotNull ConfigurationNode config,
            final @NotNull ActionParser<Audience> parser,
            final @NotNull Logger logger
    ) {

        final Map<String, List<Rule>> parsed = new Object2ObjectOpenHashMap<>();
        for (final Map.Entry<Object, ? extends ConfigurationNode> group : config.node("punishments").childrenMap().entrySet()) {
            final String name = String.valueOf(group.getKey());
            final List<String> checks = strings(group.getValue().node("checks"), logger);
            if (checks.isEmpty()) {
                logger.warn("Punishment group '{}' has no checks, skipping", name);
                continue;
            }
            final List<Rule> groupRules = new ObjectArrayList<>();
            readRules(name, group.getValue().node("at"), false, groupRules, parser, logger);
            readRules(name, group.getValue().node("every"), true, groupRules, parser, logger);
            for (final String check : checks) {
                parsed.computeIfAbsent(check.toLowerCase(Locale.ROOT), key -> new ObjectArrayList<>()).addAll(groupRules);
            }
        }

        final Map<String, RuleSet> converted = new Object2ObjectOpenHashMap<>(parsed.size());
        final Map<String, List<PunishmentRule>> api = new Object2ObjectOpenHashMap<>(parsed.size());
        for (final Map.Entry<String, List<Rule>> entry : parsed.entrySet()) {
            final RuleSet set = RuleSet.of(entry.getValue());
            converted.put(entry.getKey(), set);
            api.put(entry.getKey(), set.api());
        }
        return new CompiledCatalog(converted, Collections.unmodifiableMap(api));

    }

    public static @NotNull List<ActionStep> compileActions(
            final @NotNull List<String> lines,
            final @NotNull ActionParser<Audience> parser
    ) {

        final List<ActionStep> steps = new ObjectArrayList<>();
        final List<String> common = new ObjectArrayList<>();
        for (final String line : lines) {
            final SpecialKind kind = specialKind(line);
            if (kind != SpecialKind.NONE) {
                if (!common.isEmpty()) {
                    steps.add(new ActionStep(null, SpecialKind.NONE, "", parser.parseAll(common)));
                    common.clear();
                }
                steps.add(new ActionStep(line, kind, specialArguments(line), null));
            } else {
                common.add(line);
            }
        }
        if (!common.isEmpty()) {
            steps.add(new ActionStep(null, SpecialKind.NONE, "", parser.parseAll(common)));
        }
        return List.copyOf(steps);

    }

    private static void readRules(
            final @NotNull String group,
            final @NotNull ConfigurationNode node,
            final boolean repeating,
            final @NotNull List<Rule> out,
            final @NotNull ActionParser<Audience> parser,
            final @NotNull Logger logger
    ) {

        for (final Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            final String key = String.valueOf(entry.getKey()).trim();
            final int separator = key.indexOf(PERIOD_SEPARATOR);
            final int start;
            final int period;
            try {
                if (separator == -1) {
                    start = Integer.parseInt(key);
                    period = repeating ? start : 0;
                } else {
                    start = Integer.parseInt(key.substring(0, separator).trim());
                    period = Integer.parseInt(key.substring(separator + 1).trim());
                }
            } catch (final NumberFormatException ex) {
                logger.warn("Invalid punishment level '{}' in group '{}'", key, group);
                continue;
            }
            if (start <= 0 || (repeating && period <= 0)) {
                logger.warn("Punishment level '{}' in group '{}' must be positive", key, group);
                continue;
            }
            final List<String> lines = strings(entry.getValue(), logger);
            out.add(new Rule(start, period, List.copyOf(lines), compileActions(lines, parser)));
        }

    }

    private static @NotNull List<String> strings(
            final @NotNull ConfigurationNode node,
            final @NotNull Logger logger
    ) {

        try {
            final List<String> list = node.getList(String.class);
            return list == null ? List.of() : list;
        } catch (final SerializationException ex) {
            logger.warn("Could not read {} in punishments.yml", node.path(), ex);
            return List.of();
        }

    }

    private static @NotNull SpecialKind specialKind(
            final @NotNull String line
    ) {

        final String trimmed = line.trim();
        final int end = trimmed.indexOf(']');
        if (!trimmed.startsWith("[") || end <= 0) {
            return SpecialKind.NONE;
        }
        return switch (trimmed.substring(1, end).toLowerCase(Locale.ROOT)) {
            case "alert" -> SpecialKind.ALERT;
            case "log" -> SpecialKind.LOG;
            case "reset-vl", "reset" -> SpecialKind.RESET_VL;
            case "console", "cmd" -> SpecialKind.CONSOLE;
            default -> SpecialKind.NONE;
        };

    }

    private static @NotNull String specialArguments(
            final @NotNull String line
    ) {

        return line.substring(line.indexOf(']') + 1).trim();

    }

}
