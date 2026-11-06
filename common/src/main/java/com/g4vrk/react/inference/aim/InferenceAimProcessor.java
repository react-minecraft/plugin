package com.g4vrk.react.inference.aim;

import com.g4vrk.react.inference.http.LimitedBodyHandler;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
        return model.readResponse(bytes).withModel(
                response.headers().firstValue("X-React-Model-Family").orElse(""),
                response.headers().firstValue("X-React-Model-Name").orElse(""));
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
