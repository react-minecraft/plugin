package com.g4vrk.react.color;

import com.g4vrk.react.util.ProbabilityFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

/**
 * Colors a cheat probability from green at {@code 0} through yellow to red at {@code 1}.
 */
public final class ProbabilityColor {

    private ProbabilityColor() {
    }

    /**
     * Picks the color of a probability.
     *
     * @param probability the probability, clamped to {@code 0..1}
     * @return the color
     */
    public static @NotNull TextColor color(final double probability) {
        final double normalized = Math.min(1.0D, Math.max(probability, 0.0D));

        if (normalized < 0.5D) {
            return TextColor.color((int) (normalized * 2.0D * 255), 255, 0);
        }

        return TextColor.color(255, (int) ((1.0D - (normalized - 0.5D) * 2.0D) * 255), 0);
    }

    /**
     * Formats a probability as colored text.
     *
     * @param probability the probability
     * @return the number in its color
     */
    public static @NotNull Component component(final double probability) {
        return Component.text(ProbabilityFormatter.format(probability), color(probability));
    }

    /**
     * Formats a probability as a MiniMessage string, for places that take text instead of components.
     *
     * @param probability the probability
     * @return the number wrapped in its color tag
     */
    public static @NotNull String miniMessage(final double probability) {
        final String hex = color(probability).asHexString();

        return "<" + hex + ">" + ProbabilityFormatter.format(probability) + "</" + hex + ">";
    }

}
