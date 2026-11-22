package com.g4vrk.react.action;

import com.g4vrk.functionalActions.Action;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.Function;

/**
 * Plays configured sound keys on Adventure audiences using a platform-supplied key resolver.
 */
public final class PlaySoundAction implements Action<Audience> {

    private final Logger logger;
    private final Function<String, Key> keyResolver;

    public PlaySoundAction(
            final @NotNull Logger logger,
            final @NotNull Function<String, Key> keyResolver
    ) {

        this.logger = logger;
        this.keyResolver = keyResolver;

    }

    @Override
    public void execute(
            final @NotNull Audience audience,
            final @Nullable String arguments
    ) {

        if (arguments == null || arguments.isBlank()) {
            return;
        }

        final String[] parts = arguments.split(";", 3);
        try {
            audience.playSound(Sound.sound(
                    keyResolver.apply(parts[0].trim()),
                    Sound.Source.MASTER,
                    number(parts, 1),
                    number(parts, 2)
            ));
        } catch (final IllegalArgumentException | InvalidKeyException failure) {
            logger.warn("Invalid sound action: {}", arguments, failure);
        }

    }

    private static float number(
            final @NotNull String[] parts,
            final int index
    ) {

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
