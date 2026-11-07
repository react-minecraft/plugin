package com.g4vrk.react.punish;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.channel.ReactChannels;
import com.g4vrk.react.api.event.check.CheckPunishEvent;
import com.g4vrk.react.check.Check;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Runs the rules of {@code punishments.yml} when a check's violations grow.
 *
 * <p>{@code at: {N: [...]}} runs once when the level crosses {@code N}. {@code every: {M: [...]}}
 * runs at each multiple of {@code M}, and {@code every: {"N;M": [...]}} at {@code N}, then every
 * {@code M}. Lines use the common actions with {@code <player>}, {@code <check>} and {@code <vl>};
 * {@code [alert]}, {@code [log]}, {@code [reset-vl]} and {@code [console]} are handled here.
 * A cancelled {@link CheckPunishEvent} skips a rule.</p>
 */
public final class PunishmentManager implements ReloadObserver<ConfigurationNode> {

    private static final char PERIOD_SEPARATOR = ';';

    private final Logger logger;
    private final Server server;
    private final Scheduler scheduler;
    private final ActionParser<Audience> parser;

    private volatile Map<String, List<Rule>> rules = Map.of();

    public PunishmentManager(
            final @NotNull Logger logger,
            final @NotNull Server server,
            final @NotNull Scheduler scheduler,
            final @NotNull ActionParser<Audience> parser
    ) {
        this.logger = logger;
        this.server = server;
        this.scheduler = scheduler;
        this.parser = parser;
        this.reload();
    }

    public void reload() {
        this.onReload(React.INSTANCE.configs().punishments());
    }

    @Override
    public void onReload(final @NotNull ConfigurationNode config) {
        final Map<String, List<Rule>> parsed = new Object2ObjectOpenHashMap<>();
        for (final Map.Entry<Object, ? extends ConfigurationNode> group : config.node("punishments").childrenMap().entrySet()) {
            final String name = String.valueOf(group.getKey());
            final List<String> checks = strings(group.getValue().node("checks"));
            if (checks.isEmpty()) {
                logger.warn("Punishment group '{}' has no checks, skipping", name);
                continue;
            }
            final List<Rule> groupRules = new ObjectArrayList<>();
            readRules(name, group.getValue().node("at"), false, groupRules);
            readRules(name, group.getValue().node("every"), true, groupRules);
            for (final String check : checks) {
                parsed.computeIfAbsent(check.toLowerCase(Locale.ROOT), key -> new ObjectArrayList<>()).addAll(groupRules);
            }
        }
        this.rules = parsed;
    }

    private void readRules(
            final @NotNull String group,
            final @NotNull ConfigurationNode node,
            final boolean repeating,
            final @NotNull List<Rule> out
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
            final List<String> lines = strings(entry.getValue());
            final List<String> special = new ObjectArrayList<>();
            final List<String> common = new ObjectArrayList<>();
            for (final String line : lines) {
                (isSpecial(line) ? special : common).add(line);
            }
            out.add(new Rule(start, period, List.copyOf(lines), List.copyOf(special), parser.parseAll(common)));
        }
    }

    private @NotNull List<String> strings(final @NotNull ConfigurationNode node) {
        try {
            final List<String> list = node.getList(String.class);
            return list == null ? List.of() : list;
        } catch (final Exception ex) {
            logger.warn("Could not read {} in punishments.yml", node.path(), ex);
            return List.of();
        }
    }

    private static boolean isSpecial(final @NotNull String line) {
        return switch (tag(line)) {
            case "alert", "log", "reset-vl", "reset", "console", "cmd" -> true;
            default -> false;
        };
    }

    private static @NotNull String tag(final @NotNull String line) {
        final String trimmed = line.trim();
        final int end = trimmed.indexOf(']');
        return trimmed.startsWith("[") && end > 0 ? trimmed.substring(1, end).toLowerCase(Locale.ROOT) : "";
    }

    public void handleFail(
            final @NotNull Check check,
            final double oldViolations,
            final double newViolations
    ) {
        if (newViolations <= oldViolations) {
            return;
        }
        final List<Rule> checkRules = this.rules.get(check.id().toLowerCase(Locale.ROOT));
        if (checkRules == null) {
            return;
        }
        for (final Rule rule : checkRules) {
            final int vl = rule.trigger(oldViolations, newViolations);
            if (vl != -1 && !React.INSTANCE.events().post(new CheckPunishEvent(check, vl, rule.lines())).cancelled()) {
                execute(check, rule, vl);
            }
        }
    }

    private void execute(final @NotNull Check check, final @NotNull Rule rule, final int vl) {
        final String player = check.player().name();
        final String level = String.valueOf(vl);
        final UnaryOperator<String> placeholders = text -> text
                .replace("<player>", player)
                .replace("<check>", check.name())
                .replace("<vl>", level);
        try {
            for (final String line : rule.special()) {
                runSpecial(check, line, placeholders.apply(line.substring(line.indexOf(']') + 1).trim()), level);
            }
            final Audience audience = check.player().bukkitPlayer;
            rule.actions().run(audience, placeholders::apply);
        } catch (final Throwable th) {
            logger.error("Could not run a punishment for {} ({}, VL {})", player, check.name(), vl, th);
        }
    }

    private void runSpecial(
            final @NotNull Check check,
            final @NotNull String line,
            final @NotNull String args,
            final @NotNull String level
    ) {
        switch (tag(line)) {
            case "alert" -> ReactChannels.ALERTS.publish(check.report(args.isEmpty() ? "VL " + level : args));
            case "log" -> logger.info("[Punishment] {} reached VL {} on {}", check.player().name(), level, check.name());
            case "reset-vl", "reset" -> check.resetViolations();
            default -> {
                if (!args.isEmpty()) {
                    scheduler.schedule(() -> server.dispatchCommand(server.getConsoleSender(), args), TickSchedule.instant());
                }
            }
        }
    }

    private record Rule(
            int start,
            int period,
            @NotNull List<String> lines,
            @NotNull List<String> special,
            @NotNull ExecutableActionList<? super Audience> actions
    ) {

        private int trigger(final double oldVl, final double newVl) {
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

}
