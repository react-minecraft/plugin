package com.g4vrk.react.channel;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.channel.CheckReport;
import com.g4vrk.react.api.channel.impl.ReportChannel;
import com.g4vrk.react.config.model.CheckConfig;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Shows debug lines with the {@code debug} actions of the check they came from.
 *
 * <p>Placeholders: {@code <player>}, {@code <check>}, {@code <violations>} (also {@code <vl>})
 * and {@code <details>}, the line itself.</p>
 */
public final class DebugActions implements ReportChannel.Delivery {

    private final ActionParser<Audience> parser;

    private volatile Map<String, ExecutableActionList<? super Audience>> actions = Map.of();

    public DebugActions(final @NotNull ActionParser<Audience> parser, final @NotNull Map<String, CheckConfig> checks) {
        this.parser = parser;
        this.checks(checks);
    }

    public void checks(final @NotNull Map<String, CheckConfig> checks) {
        final Map<String, ExecutableActionList<? super Audience>> parsed = new Object2ObjectOpenHashMap<>(checks.size());
        checks.forEach((id, config) -> parsed.put(id, parser.parseAll(config.debug())));
        this.actions = parsed;
    }

    @Override
    public void deliver(final @NotNull Audience recipient, final @NotNull CheckReport report) {
        final ExecutableActionList<? super Audience> list = this.actions.get(report.checkId());
        if (list == null) {
            return;
        }
        final String violations = String.valueOf(report.violations());

        list.run(recipient, text -> text
                .replace("<player>", report.playerName())
                .replace("<check>", report.check())
                .replace("<violations>", violations)
                .replace("<vl>", violations)
                .replace("<details>", report.details()));
    }

}
