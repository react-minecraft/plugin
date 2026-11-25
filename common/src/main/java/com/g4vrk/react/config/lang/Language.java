package com.g4vrk.react.config.lang;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Bundled localization bundles available inside React's plugin resources.
 */
public enum Language {

    EN,
    RU;

    public static @NotNull Language resolve() {

        try {
            final String languageStr = System.getProperty("user.language").toUpperCase(Locale.ROOT);

            return Language.valueOf(languageStr);
        } catch (final Throwable ignored) {
            return RU;
        }

    }

}
