package com.g4vrk.react.history.printer;

import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.react.color.ProbabilityColor;
import com.g4vrk.react.config.model.HistoryConfig;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.util.ProbabilityFormatter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Prints {@code /react history} pages from {@code history.yml}.
 *
 * <p>Every line knows {@code <player>}, {@code <size>}, {@code <page>}, {@code <pages>},
 * {@code <avg-probability>} (colored) and {@code <avg-probability-plain>}, plus the clickable
 * {@code <prev>} and {@code <next>} that open the neighbour pages. Entry lines also know
 * {@code <probability>}, {@code <probability-plain>} and {@code <check>}.</p>
 */
public final class InferenceHistoryPrinter implements ReloadObserver<HistoryConfig> {

    private final TextFormatter formatter;

    private volatile HistoryConfig.Format format;

    public InferenceHistoryPrinter(final @NotNull TextFormatter formatter) {
        this.formatter = formatter;
        this.reload();
    }

    public void reload() {
        this.onReload(React.INSTANCE.configs().history());
    }

    @Override
    public void onReload(final @NotNull HistoryConfig config) {
        this.format = config.format();
    }

    public void print(final @NotNull Audience receiver, final @NotNull TrackedPlayerImpl player) {
        this.print(receiver, player, 1);
    }

    public void print(final @NotNull Audience receiver, final @NotNull TrackedPlayerImpl player, final int requested) {
        final HistoryConfig.Format format = this.format;
        final int perPage = Math.max(1, format.printEntries());
        final InferenceHistoryEntry[] history = player.inferenceHistory.entries();
        final int pages = Math.max(1, (int) Math.ceil((double) history.length / perPage));
        final int page = Math.max(1, Math.min(requested, pages));
        final double average = player.inferenceStatistic.averageProbability();

        final TagResolver common = TagResolver.resolver(
                Placeholder.unparsed("player", player.name()),
                Placeholder.unparsed("size", Integer.toString(history.length)),
                Placeholder.unparsed("page", Integer.toString(page)),
                Placeholder.unparsed("pages", Integer.toString(pages)),
                Placeholder.component("avg-probability", ProbabilityColor.component(average)),
                Placeholder.unparsed("avg-probability-plain", ProbabilityFormatter.format(average)),
                pageLink("prev", player, Math.max(1, page - 1)),
                pageLink("next", player, Math.min(pages, page + 1))
        );

        this.send(receiver, format.header(), common);

        if (history.length == 0) {
            this.send(receiver, format.empty(), common);
        } else {
            final int start = history.length - 1 - (page - 1) * perPage;
            final int end = Math.max(-1, start - perPage);

            for (int index = start; index > end; index--) {
                final InferenceHistoryEntry entry = history[index];

                this.send(receiver, format.entryFormat(), TagResolver.resolver(
                        common,
                        Placeholder.component("probability", ProbabilityColor.component(entry.getProbability())),
                        Placeholder.unparsed("probability-plain", ProbabilityFormatter.format(entry.getProbability())),
                        Placeholder.unparsed("check", entry.getCheck().name())
                ));
            }
        }

        this.send(receiver, format.footer(), common);
    }

    private static @NotNull TagResolver pageLink(
            final @NotNull String name,
            final @NotNull TrackedPlayerImpl player,
            final int page
    ) {
        return TagResolver.resolver(name, Tag.styling(ClickEvent.runCommand("/react history " + player.name() + " " + page)));
    }

    private void send(
            final @NotNull Audience receiver,
            final @NotNull List<String> lines,
            final @NotNull TagResolver resolver
    ) {
        for (final String line : lines) {
            receiver.sendMessage(formatter.format(line, resolver));
        }
    }

}
