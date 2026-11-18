package com.g4vrk.react.action;

import com.cryptomorin.xseries.XSound;
import com.g4vrk.functionalActions.Action;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Keyed;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Plays configured sound keys across Bukkit versions that expose Sound as an enum or interface.
 */
public final class PlaySoundAction implements Action<Audience> {

    private final Logger logger;

    public PlaySoundAction(final @NotNull Logger logger) {

        this.logger = logger;

    }

    @Override
    public void execute(final @NotNull Audience audience, final @Nullable String arguments) {

        if (arguments == null || arguments.isBlank()) {
            return;
        }

        final String[] parts = arguments.split(";", 3);
        try {
            audience.playSound(Sound.sound(key(parts[0].trim()), Sound.Source.MASTER, number(parts, 1), number(parts, 2)));
        } catch (final IllegalArgumentException failure) {
            logger.warn("Invalid sound action: {}", arguments, failure);
        }

    }

    private static @NotNull Key key(final @NotNull String value) {

        if (value.indexOf(':') < 0 && value.indexOf('.') < 0) {
            final Keyed sound = XSound.matchXSound(value).map(XSound::parseSound).orElse(null);
            if (sound != null) {
                return Key.key(sound.getKey().toString());
            }
        }
        return Key.key(value);

    }

    private static float number(final @NotNull String[] parts, final int index) {

        if (parts.length <= index) {
            return 1.0f;
        }
        try {
            return Float.parseFloat(parts[index]);
        } catch (final NumberFormatException ignored) {
            return 1.0f;
        }

    }

}
