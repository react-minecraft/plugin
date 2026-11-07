package com.g4vrk.react.api.addon.exception;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.AvailableSince("2.0")
public class InvalidAddonException extends RuntimeException {

    public InvalidAddonException() {
    }

    public InvalidAddonException(
            @NotNull String message
    ) {
        super(message);
    }

    public InvalidAddonException(
            @NotNull String message,
            @NotNull Throwable cause
    ) {
        super(message, cause);
    }

    public InvalidAddonException(
            @NotNull Throwable cause
    ) {
        super(cause);
    }

}
