package com.g4vrk.react.storage.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public record PlayerStorageData(
        @NotNull Map<String, StoredViolation> violations,
        @NotNull List<StoredInference> inferenceHistory,
        @NotNull Map<String, Long> bypasses
) {
    public static final PlayerStorageData EMPTY = new PlayerStorageData(Map.of(), List.of(), Map.of());

    public PlayerStorageData {
        violations = Map.copyOf(violations);
        inferenceHistory = List.copyOf(inferenceHistory);
        bypasses = Map.copyOf(bypasses);
    }
}
