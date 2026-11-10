package com.g4vrk.react.history.printer;

import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.react.api.text.TagMapper;
import com.g4vrk.react.color.ProbabilityColor;
import com.g4vrk.react.config.model.HistoryConfig;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.util.ProbabilityFormatter;
import net.kyori.adventure.audience.Audience;
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

        final TagMapper common = new TagMapper() {

            @Override
            public boolean has(final @NotNull String name) {
                return switch (name) {
                    case "player", "size", "page", "pages", "avg-probability", "avg-probability-plain" -> true;
                    default -> false;
                };
            }

            @Override
            public String map(final @NotNull String name, final @NotNull String args) {
                return switch (name) {
                    case "player" -> player.name();
                    case "size" -> Integer.toString(history.length);
                    case "page" -> Integer.toString(page);
                    case "pages" -> Integer.toString(pages);
                    case "avg-probability" -> ProbabilityColor.miniMessage(average);
                    case "avg-probability-plain" -> ProbabilityFormatter.format(average);
                    default -> null;
                };
            }

        };
        final String previous = pageLink(player, Math.max(1, page - 1));
        final String next = pageLink(player, Math.min(pages, page + 1));

        this.send(receiver, format.header(), common, previous, next);

        if (history.length == 0) {
            this.send(receiver, format.empty(), common, previous, next);
        } else {
            final int start = history.length - 1 - (page - 1) * perPage;
            final int end = Math.max(-1, start - perPage);

            for (int index = start; index > end; index--) {
                final InferenceHistoryEntry entry = history[index];

                this.send(
                        receiver,
                        format.entryFormat(),
                        new TagMapper() {

                            @Override
                            public boolean has(final @NotNull String name) {
                                return common.has(name) || switch (name) {
                                    case "probability", "probability-plain", "check" -> true;
                                    default -> false;
                                };
                            }

                            @Override
                            public String map(final @NotNull String name, final @NotNull String args) {
                                return switch (name) {
                                    case "probability" -> ProbabilityColor.miniMessage(entry.getProbability());
                                    case "probability-plain" -> ProbabilityFormatter.format(entry.getProbability());
                                    case "check" -> escape(entry.getCheck().name());
                                    default -> common.map(name, args);
                                };
                            }

                        },
                        previous,
                        next
                );
            }
        }

        this.send(receiver, format.footer(), common, previous, next);
    }

    private static @NotNull String pageLink(final @NotNull TrackedPlayerImpl player, final int page) {
        return "<click:run_command:'/react history " + player.name() + " " + page + "'>";
    }

    private static @NotNull String escape(final @NotNull String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    private void send(
            final @NotNull Audience receiver,
            final @NotNull List<String> lines,
            final @NotNull TagMapper mapper,
            final @NotNull String previous,
            final @NotNull String next
    ) {
        for (final String line : lines) {
            receiver.sendMessage(formatter.formatMapped(
                    line.replace("<prev>", previous).replace("</prev>", "</click>")
                            .replace("<next>", next).replace("</next>", "</click>"),
                    mapper
            ));
        }
    }

}
