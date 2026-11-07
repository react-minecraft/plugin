package com.g4vrk.react.placeholder;

import com.g4vrk.react.api.placeholder.PlaceholderRegistry;
import com.g4vrk.react.color.ProbabilityColor;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.statistic.InferenceStatistic;
import com.g4vrk.react.util.ProbabilityFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * PlaceholderAPI expansion with the old {@code inference-history.*} and {@code model} values.
 *
 * <p>Anything else is looked up in the {@link PlaceholderRegistry}: {@code %react_vl_aim%} asks
 * key {@code vl} with query {@code aim}.</p>
 */
public final class ReactPlaceholderExpansion extends PlaceholderExpansion {

    private final String id;
    private final String author;
    private final String version;

    private final PlayerRegistry registry;
    private final PlaceholderRegistry placeholders;
    private final Function<OfflinePlayer, String> selectedModel;

    public ReactPlaceholderExpansion(
            final @NotNull String id,
            final @NotNull String author,
            final @NotNull String version,
            final @NotNull PlayerRegistry registry,
            final @NotNull PlaceholderRegistry placeholders,
            final @NotNull Function<OfflinePlayer, String> selectedModel
    ) {
        this.id = id;
        this.author = author;
        this.version = version;
        this.registry = registry;
        this.placeholders = placeholders;
        this.selectedModel = selectedModel;
    }

    @Override
    public @NotNull String getIdentifier() {
        return id;
    }

    @Override
    public @NotNull String getAuthor() {
        return author;
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }


    @Override
    public @Nullable String onRequest(
            final OfflinePlayer offlinePlayer,
            final @NotNull String params
    ) {

        if (params.equals("model") || params.equals("model_family") || params.equals("model_name")) {
            final String display = selectedModel.apply(offlinePlayer);
            final String[] parts = display.split("/", 2);
            return switch (params) {
                case "model_family" -> parts[0];
                case "model_name" -> parts.length == 2 ? parts[1] : display;
                default -> display;
            };
        }

        if (offlinePlayer == null) {
            return null;
        }

        final TrackedPlayerImpl player =
                registry.get(offlinePlayer.getUniqueId());

        if (player == null) {
            return null;
        }

        final String prefix = "inference-history.";

        if (!params.startsWith(prefix)) {
            final int separator = params.indexOf('_');

            return separator < 0
                    ? placeholders.resolve(player, params, "")
                    : placeholders.resolve(player, params.substring(0, separator), params.substring(separator + 1));
        }

        final String path = params.substring(prefix.length());

        if (path.startsWith("entry.")) {

            final String entryPrefix = "entry.";

            final int separator = path.indexOf('.', entryPrefix.length());

            if (separator == -1) {
                return null;
            }

            final int index;

            try {
                index = Integer.parseInt(
                        path,
                        entryPrefix.length(),
                        separator,
                        10
                );
            } catch (NumberFormatException ignored) {
                return null;
            }

            final InferenceHistoryEntry entry =
                    player.inferenceHistory.get(index);

            if (entry == null) {
                return null;
            }

            return switch (path.substring(separator + 1)) {

                case "probability" -> {
                    final double probability = entry.getProbability();

                    yield ProbabilityFormatter.format(probability);
                }

                case "probability:colored" -> {
                    yield ProbabilityColor.miniMessage(entry.getProbability());
                }

                case "check" -> entry.getCheck().name();

                default -> null;
            };
        }

        final InferenceStatistic.Result statisticResult = player.inferenceStatistic.calculate();

        return switch (path) {

            case "size" ->
                    Integer.toString(player.inferenceHistory.size());

            case "avg-probability" -> ProbabilityFormatter.format(statisticResult.averageProbability());

            case "avg-probability:colored" -> {

                yield ProbabilityColor.miniMessage(statisticResult.averageProbability());
            }

            default -> null;
        };
    }
}
