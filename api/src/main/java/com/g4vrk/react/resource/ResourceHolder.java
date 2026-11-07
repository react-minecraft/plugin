package com.g4vrk.react.resource;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

@ApiStatus.AvailableSince("2.0")
public interface ResourceHolder {

    @Nullable InputStream getResource(@NotNull String fileName);

    void saveResource(@NotNull String fileName, boolean replace);

}
