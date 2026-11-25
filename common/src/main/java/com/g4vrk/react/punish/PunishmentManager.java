package com.g4vrk.react.punish;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.channel.registry.BuiltinChannelRegistry;
import com.g4vrk.react.api.event.bus.EventDispatcher;
import com.g4vrk.react.api.event.check.CheckPunishEvent;
import com.g4vrk.react.api.platform.PlatformServer;
import com.g4vrk.react.api.punish.PunishmentRule;
import com.g4vrk.react.api.punish.registry.PunishmentRegistry;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler.ActionStep;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler.CompiledCatalog;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler.Rule;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler.RuleSet;
import com.g4vrk.react.punish.rule.PunishmentRuleCompiler.SpecialKind;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Runs the rules of {@code punishments.yml} when a check's violations grow.
 *
 * <p>{@code at: {N: [...]}} runs once when the level crosses {@code N}. {@code every: {M: [...]}}
 * runs when a multiple of {@code M} is crossed, and {@code every: {"N;M": [...]}} at {@code N}, then every
 * {@code M}. One increase triggers a rule at most once, at the highest crossed level. Lines use
 * the common actions with {@code <player>}, {@code <check>} and {@code <vl>};
 * {@code [alert]}, {@code [log]}, {@code [reset-vl]} and {@code [console]} are handled here.
 * Actions start in configuration order; console commands are scheduled and may finish later.
 * A cancelled {@link CheckPunishEvent} skips a rule.</p>
 */
public final class PunishmentManager implements PunishmentRegistry, ReloadObserver<ConfigurationNode> {

    private final Logger logger;
    private final PlatformServer server;
    private final TaskScheduler<PlatformPlayer> scheduler;
    private final ActionParser<Audience> parser;
    private final EventDispatcher events;

    private volatile Map<String, RuleSet> rules = Map.of();
    private volatile Map<String, List<PunishmentRule>> view = Map.of();

    public PunishmentManager(
            final @NotNull Logger logger,
            final @NotNull PlatformServer server,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull ActionParser<Audience> parser,
            final @NotNull EventDispatcher events
    ) {

        this.events = events;
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
    public void onReload(
            final @NotNull ConfigurationNode config
    ) {

        final CompiledCatalog catalog = PunishmentRuleCompiler.compile(config, parser, logger);
        this.rules = catalog.rules();
        this.view = catalog.apiView();

    }

    static @NotNull List<ActionStep> compileActions(
            final @NotNull List<String> lines,
            final @NotNull ActionParser<Audience> parser
    ) {

        return PunishmentRuleCompiler.compileActions(lines, parser);

    }

    static void runActions(
            final @NotNull List<ActionStep> steps,
            final @NotNull Audience audience,
            final @NotNull UnaryOperator<String> placeholders,
            final @NotNull Consumer<String> special
    ) {

        for (final ActionStep step : steps) {
            if (step.special() != null) {
                special.accept(step.special());
            } else {
                step.actions().run(audience, placeholders);
            }
        }

    }

    @Override
    public @Nullable List<PunishmentRule> get(
            final @NotNull String check
    ) {

        return this.view.get(check);

    }

    @Override
    public @Nullable String getKey(
            final @NotNull List<PunishmentRule> value
    ) {

        for (final Map.Entry<String, List<PunishmentRule>> entry : this.view.entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }
        return null;

    }

    @Override
    public @NotNull Set<String> keySet() {

        return this.view.keySet();

    }

    @Override
    public @NotNull Set<Map.Entry<String, List<PunishmentRule>>> entrySet() {

        return this.view.entrySet();

    }

    @Override
    public @NotNull Stream<List<PunishmentRule>> stream() {

        return this.view.values().stream();

    }

    @Override
    public int size() {

        return this.view.size();

    }

    @Override
    public @NotNull Iterator<List<PunishmentRule>> iterator() {

        return this.view.values().iterator();

    }

    public void handleFail(
            final @NotNull Check check,
            final double oldViolations,
            final double newViolations
    ) {

        if (newViolations <= oldViolations) {
            return;
        }
        final RuleSet set = this.rules.get(check.id().toLowerCase(Locale.ROOT));
        if (set == null) {
            return;
        }
        for (final Rule rule : set.rules()) {
            final int vl = rule.trigger(oldViolations, newViolations);
            if (vl != -1 && !events.post(new CheckPunishEvent(check, vl, rule.lines())).cancelled()) {
                execute(check, rule, vl);
            }
        }

    }

    private void execute(
            final @NotNull Check check,
            final @NotNull Rule rule,
            final int vl
    ) {

        final String player = check.player().name();
        final String level = String.valueOf(vl);
        final UnaryOperator<String> placeholders = text -> text
                .replace("<player>", player)
                .replace("<check>", check.name())
                .replace("<vl>", level);
        try {
            final Audience audience = check.player().platform();
            for (final ActionStep step : rule.actions()) {
                if (step.kind() == SpecialKind.NONE) {
                    step.actions().run(audience, placeholders);
                    continue;
                }
                final String args = step.arguments().isEmpty() ? "" : placeholders.apply(step.arguments());
                runSpecial(check, step.kind(), args, level);
            }
        } catch (final Throwable th) {
            logger.error("Could not run a punishment for {} ({}, VL {})", player, check.name(), vl, th);
        }

    }

    private void runSpecial(
            final @NotNull Check check,
            final @NotNull SpecialKind kind,
            final @NotNull String args,
            final @NotNull String level
    ) {

        switch (kind) {
            case ALERT -> BuiltinChannelRegistry.ALERTS.publish(check.report(args.isEmpty() ? "VL " + level : args));
            case LOG -> logger.info("[Punishment] {} reached VL {} on {}", check.player().name(), level, check.name());
            case RESET_VL -> check.resetViolations();
            case CONSOLE -> {
                if (!args.isEmpty()) {
                    scheduler.schedule(() -> server.dispatchConsoleCommand(args), TickSchedule.instant());
                }
            }
            case NONE -> {
            }
        }

    }

}
