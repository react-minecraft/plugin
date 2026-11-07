package com.g4vrk.react.channel;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.channel.CheckReport;
import com.g4vrk.react.api.channel.impl.ReportChannel;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Runs an action list from {@code actions.yml} for every report a recipient gets.
 *
 * <p>Placeholders: {@code <player>}, {@code <check>}, {@code <violations>} (also {@code <vl>})
 * and {@code <details>}.</p>
 */
public final class ReportActions implements ReportChannel.Delivery {

    private final ActionParser<Audience> parser;

    private volatile ExecutableActionList<? super Audience> actions;

    public ReportActions(final @NotNull ActionParser<Audience> parser, final @NotNull List<String> actions) {
        this.parser = parser;
        this.actions(actions);
    }

    public void actions(final @NotNull List<String> actions) {
        this.actions = this.parser.parseAll(actions);
    }

    @Override
    public void deliver(final @NotNull Audience recipient, final @NotNull CheckReport report) {
        final String violations = String.valueOf(report.violations());

        this.actions.run(recipient, text -> text
                .replace("<player>", report.playerName())
                .replace("<check>", report.check())
                .replace("<violations>", violations)
                .replace("<vl>", violations)
                .replace("<details>", report.details()));
    }

}
