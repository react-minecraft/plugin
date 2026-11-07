package com.g4vrk.react.api.rcp.meta;

import com.g4vrk.react.api.Metadata;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.AvailableSince("2.0")
public record RCPMetadata(
        @NotNull String name,
        @NotNull String version,
        @NotNull String mainClass,
        int apiVersion
) implements Metadata {}
