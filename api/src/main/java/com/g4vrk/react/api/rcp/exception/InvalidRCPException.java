package com.g4vrk.react.api.rcp.exception;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.AvailableSince("2.0")
public class InvalidRCPException extends RuntimeException {

    public InvalidRCPException() {
    }

    public InvalidRCPException(
            @NotNull String message
    ) {
        super(message);
    }

    public InvalidRCPException(
            @NotNull String message,
            @NotNull Throwable cause
    ) {
        super(message, cause);
    }

    public InvalidRCPException(
            @NotNull Throwable cause
    ) {
        super(cause);
    }

}
