package com.g4vrk.react.inference.aim;

import com.g4vrk.react.inference.model.ModelId;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

@Value
public class InferenceResult {

    private static final InferenceResult UNAVAILABLE = new InferenceResult(-1.0D, Double.NaN, true, "", "");

    double probability;
    double decisionThreshold;
    boolean enforcementEnabled;
    @NotNull String modelFamily;
    @NotNull String modelName;

    /** A model's verdict; the processor attaches which model gave it. */
    public static @NotNull InferenceResult of(double probability, double decisionThreshold, boolean enforcementEnabled) {
        return new InferenceResult(probability, decisionThreshold, enforcementEnabled, "", "");
    }

    public @NotNull InferenceResult withModel(final @NotNull String family, final @NotNull String name) {
        return new InferenceResult(probability, decisionThreshold, enforcementEnabled, family, name);
    }

    public String modelDisplay() {
        final ModelId model = ModelId.fromIdentity(modelFamily + "/" + modelName);
        return model == null ? "Model unknown" : model.displayName();
    }

    public boolean isAvailable() {
        return probability >= 0.0D;
    }

    public static @NotNull InferenceResult unavailable() {
        return UNAVAILABLE;
    }
}
