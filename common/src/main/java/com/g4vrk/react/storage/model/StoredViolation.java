package com.g4vrk.react.storage.model;

/**
 * Persisted check violation value paired with its last-updated epoch timestamp.
 */
public record StoredViolation(
        double value,
        long updatedAt
) {

}
