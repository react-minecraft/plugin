package com.g4vrk.react.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.List;

@ConfigSerializable
public record HistoryConfig(History history) {

    public Format format() {
        return history.inference().format();
    }

    @ConfigSerializable
    public record History(Inference inference) {
    }

    @ConfigSerializable
    public record Inference(Format format) {
    }

    @ConfigSerializable
    public record Format(
            int printEntries,
            List<String> header,
            List<String> empty,
            List<String> entryFormat,
            List<String> footer
    ) {
    }

}
