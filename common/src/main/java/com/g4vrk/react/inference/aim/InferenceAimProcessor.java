package com.g4vrk.react.inference.aim;

import com.g4vrk.react.inference.http.LimitedBodyHandler;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.json.InferenceJsonCodec;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class InferenceAimProcessor {

    private final Logger logger;
    private final InferenceServer inferenceServer;
    private final Scheduler scheduler;

    public InferenceAimProcessor(
            @NotNull Logger logger,
            @NotNull InferenceServer inferenceServer,
            @NotNull Scheduler scheduler
    ) {
        this.logger = logger;
        this.inferenceServer = inferenceServer;
        this.scheduler = scheduler;
    }

    public long requestEpoch() {
        return inferenceServer.requestEpoch();
    }

    public void publishIfCurrent(final long epoch, final @NotNull Runnable publication) {
        inferenceServer.publishIfCurrent(epoch, publication);
    }

    public boolean isModelEnabled(final @NotNull ModelId model) {
        return inferenceServer.isModelEnabled(model);
    }

    /** Sends one window a model encoded; the token is null when the key goes in a header. */
    public void check(
            final @NotNull String playerName,
            final @NotNull AimModel model,
            final @NotNull Function<@Nullable String, HttpRequest> encoder,
            final @NotNull Consumer<InferenceResult> resultHandler
    ) {
        if (!inferenceServer.isAcceptingRequests()) {
            complete(resultHandler, InferenceResult.unavailable());
            return;
        }
        try {
            enqueue(playerName, encoder.apply(inferenceServer.getAuthBodyToken()), model, resultHandler);
        } catch (RuntimeException failure) {
            logger.warn("Could not enqueue {} request for {}", model.id().displayName(), playerName, failure);
            complete(resultHandler, InferenceResult.unavailable());
        }
    }

    private void enqueue(
            final String playerName,
            final HttpRequest request,
            final AimModel model,
            final Consumer<InferenceResult> originalResultHandler
    ) {
        final long requestEpoch = inferenceServer.requestEpoch();
        final Consumer<InferenceResult> resultHandler = result -> {
            if (requestEpoch == inferenceServer.requestEpoch()) originalResultHandler.accept(result);
        };
        inferenceServer.callAsync(request, model.id()).whenComplete((response, failure) -> {
            if (failure != null) {
                if (LimitedBodyHandler.isTooLarge(failure)) {
                    logger.warn("ML server response is too large for {}", playerName);
                } else if (!(failure instanceof CancellationException)) {
                    logger.warn("Could not send {} request for {}", model.id().displayName(), playerName, failure);
                }
                complete(resultHandler, InferenceResult.unavailable());
                return;
            }
            try {
                complete(resultHandler, read(playerName, model, response));
            } catch (Exception ex) {
                logger.warn("Could not process ML response for {}", playerName, ex);
                complete(resultHandler, InferenceResult.unavailable());
            }
        });
    }

    private InferenceResult read(final String playerName, final AimModel model, final HttpResponse<byte[]> response) {
        final byte[] bytes = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.warn("ML server rejected request for {}: HTTP {} {}",
                    playerName, response.statusCode(), new String(bytes, StandardCharsets.UTF_8));
            return InferenceResult.unavailable();
        }
        final InferenceResult binary = model.readBinary(bytes, response);
        if (binary != null) {
            return binary;
        }
        final String responseText = new String(bytes, StandardCharsets.UTF_8);
        if (responseText.isBlank()) {
            logger.warn("ML server returned an empty response for {}", playerName);
            return InferenceResult.unavailable();
        }

        final Map<String, Object> result = InferenceJsonCodec.readObject(responseText);
        final Double probability = readNumber(result, "cheat_probability");
        if (probability == null || !Double.isFinite(probability) || probability < 0.0D || probability > 1.0D) {
            logger.warn("ML response does not contain a valid 'cheat_probability' field for {}: {}",
                    playerName, responseText);
            return InferenceResult.unavailable();
        }

        final Double threshold = readNumber(result, "decision_threshold");
        if (threshold == null && model.usesLocalThreshold()) {
            // The answer carries no threshold: aim-ai.yml alert.threshold decides.
            return new InferenceResult(probability, Double.NaN, true,
                    readModelPart(result, "model_family"), readModelPart(result, "model_name"));
        }
        final Object enabled = result.get("enforcement_enabled");
        final Object mode = result.get("deployment_mode");
        if (threshold == null || !Double.isFinite(threshold) || threshold < 0 || threshold > 1
                || !(enabled instanceof Boolean) || !(mode instanceof String)
                || !((Boolean) enabled ? "enforce" : "observe").equals(mode)) {
            throw new IllegalArgumentException("Response requires a valid deployment mode and threshold");
        }
        return new InferenceResult(probability, threshold, (Boolean) enabled,
                readModelPart(result, "model_family"), readModelPart(result, "model_name"));
    }

    private static Double readNumber(
            final @NotNull Map<String, Object> response,
            final @NotNull String field
    ) {
        final Object raw = response.get(field);
        return raw instanceof Number number ? number.doubleValue() : null;
    }

    private static String readModelPart(final Map<String, Object> response, final String key) {
        final Object value = response.get(key);
        return value instanceof String text ? text : "";
    }

    private void complete(
            @NotNull Consumer<InferenceResult> resultHandler,
            @NotNull InferenceResult result
    ) {
        scheduler.schedule(
                () -> {
                    if (inferenceServer.isAcceptingRequests()) resultHandler.accept(result);
                },
                TickSchedule.instant()
        );
    }

}
