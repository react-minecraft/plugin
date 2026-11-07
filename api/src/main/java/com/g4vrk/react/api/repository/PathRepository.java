package com.g4vrk.react.api.repository;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collection;

@ApiStatus.AvailableSince("2.0")
public interface PathRepository {

    @NotNull Collection<Path> discover();

}
