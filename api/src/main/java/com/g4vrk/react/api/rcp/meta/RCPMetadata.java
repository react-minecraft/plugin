package com.g4vrk.react.api.rcp.meta;

import com.g4vrk.react.api.Metadata;
import org.jetbrains.annotations.NotNull;

public record RCPMetadata(
        @NotNull String name,
        @NotNull String version,
        @NotNull String mainClass,
        int apiVersion
) implements Metadata {}
