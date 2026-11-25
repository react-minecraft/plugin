package com.g4vrk.react.config.lang;

import com.g4vrk.react.config.EnvironmentConfig;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Bundled localization bundles available inside React's plugin resources.
 */
public enum Language {

    EN,
    RU;

    public static @NotNull Language resolve() {

        final String override = EnvironmentConfig.setting("react.language");
        if (override == null) {
            return "en".equalsIgnoreCase(System.getProperty("user.language")) ? EN : RU;
        }
        return switch (override.strip().toLowerCase(Locale.ROOT)) {
            case "en" -> EN;
            case "ru" -> RU;
            default -> throw new IllegalArgumentException("react.language must be en or ru");
        };

    }

}
