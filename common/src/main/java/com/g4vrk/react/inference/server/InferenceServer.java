package com.g4vrk.react.inference.server;

import com.g4vrk.react.inference.auth.AuthApplier;
import com.g4vrk.react.inference.auth.AuthSettings;
import com.g4vrk.react.inference.auth.type.AuthType;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.server.settings.InferenceEndpointSettings;
import com.g4vrk.react.inference.server.settings.InferenceSettings;
import lombok.Getter;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.g4vrk.react.inference.ModelId;
import com.g4vrk.react.util.moshi.MoshiHolder;
import java.util.Collections;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Getter
public final class InferenceServer {

    private static final String PATH = "/api/v2/inference/";
    private static final long MODEL_REFRESH_MILLIS = 60_000L;
    private static final long MAX_RESPONSE_BYTES = 64L * 1024L;

    private final OkHttpClient client;
    private final AuthApplier authApplier;

    private final String baseUrl;
    private final InferenceEndpointSettings endpoint;

    private final boolean enabled;
    private final String authBodyToken;
    private final Logger logger;
    private final String apiKey;
    private final AtomicLong nextModelRefresh = new AtomicLong();
    private volatile Set<ModelId> enabledModels = Set.of();

    private final AtomicBoolean suspended = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong requestEpoch = new AtomicLong();

    public long requestEpoch() { return requestEpoch.get(); }

    /** Serialize the FINAL publication with pause, including callbacks on entity schedulers. */
    public synchronized void publishIfCurrent(long epoch, Runnable publication) {
        if (isAcceptingRequests() && requestEpoch.get() == epoch) publication.run();
    }

    public boolean isAcceptingRequests() {
        return enabled && !suspended.get() && !closed.get();
    }

    public synchronized void pauseRequests() {
        suspended.set(true);
        requestEpoch.incrementAndGet();
        client.dispatcher().cancelAll();
    }

    public synchronized void resumeRequests() {
        if (!closed.get()) suspended.set(false);
    }

    public InferenceServer(
            final @NotNull Logger logger,
            final @NotNull InferenceSettings settings
    ) {
        this.client = settings.buildClient();
        this.enabled = settings.isEnabled();
        this.logger = logger;
        this.apiKey = settings.getAuth().isEnabled() ? settings.getAuth().getValue() : null;
        this.authBodyToken = settings.getAuth().isEnabled() && settings.getAuth().getType() == AuthType.BODY
                ? settings.getAuth().getValue() : null;

        this.endpoint = settings.getEndpoint();

        this.baseUrl = normalizeUrl(endpoint.getBaseUrl());
        this.authApplier = new AuthApplier(settings.getAuth());

        if (enabled) {
            validate(logger, settings.getAuth());
        }
    }

    /** The models enabled on the site for this server; refreshed in the background. */
    public @NotNull Set<ModelId> enabledModels() {
        if (enabled) refreshModelsIfDue();
        return enabledModels;
    }

    public boolean isModelEnabled(final @NotNull ModelId model) {
        return enabledModels().contains(model);
    }

    public String getSelectedModelDisplay() {
        if (!enabled) return "Disabled";
        final Set<ModelId> models = enabledModels();
        return models.isEmpty() ? "Unavailable"
                : models.stream().map(ModelId::displayName).collect(Collectors.joining(", "));
    }

    private void refreshModelsIfDue() {
        long now = System.currentTimeMillis();
        long next = nextModelRefresh.get();
        if (now < next || !nextModelRefresh.compareAndSet(next, now + MODEL_REFRESH_MILLIS)) return;
        if (apiKey == null || apiKey.isBlank()) {
            enabledModels = Set.of();
            return;
        }
        HttpUrl url = apiUrl("/api/v1/auth/check");
        if (url == null) return;
        Request request = new Request.Builder().url(url).header("Authorization", "Bearer " + apiKey).get().build();
        try {
            noRedirectClient().newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException exception) {
                    // A network failure keeps the last known models instead of stopping every check.
                    logger.debug("Could not refresh the enabled models", exception);
                }
                @Override public void onResponse(Call call, Response response) {
                    try (response; ResponseBody body = response.body()) {
                        if (response.code() == 401 || response.code() == 403) {
                            enabledModels = Set.of();
                            logger.warn("The server key was rejected: link it again with /react connect <code>");
                            return;
                        }
                        if (!response.isSuccessful() || body == null || body.source().request(MAX_RESPONSE_BYTES)) return;
                        Map<String, Object> json = MoshiHolder.RESPONSE_ADAPTER.fromJson(body.string());
                        if (json != null) enabledModels = readEnabledModels(json);
                    } catch (Exception exception) {
                        logger.debug("Could not read the enabled models", exception);
                    }
                }
            });
        } catch (RuntimeException exception) {
            logger.debug("Could not refresh the enabled models", exception);
        }
    }

    private static Set<ModelId> readEnabledModels(final Map<String, Object> json) {
        final EnumSet<ModelId> models = EnumSet.noneOf(ModelId.class);
        if (json.get("models") instanceof List<?> items) {
            for (final Object item : items) {
                if (item instanceof Map<?, ?> entry && Boolean.TRUE.equals(entry.get("enabled"))) {
                    final ModelId model = ModelId.fromIdentity(String.valueOf(entry.get("id")));
                    if (model != null) models.add(model);
                }
            }
        }
        return Collections.unmodifiableSet(models);
    }

    /** The outcome of /react connect check; a model missing from models is one this key has no access to. */
    public record ConnectionStatus(String server, Map<ModelId, Boolean> models, String problem) {
        static ConnectionStatus failed(final String problem) {
            return new ConnectionStatus("", Map.of(), problem);
        }
    }

    /** Asks the site about this server's key right now and updates the enabled models. */
    public @NotNull CompletableFuture<ConnectionStatus> checkConnection() {
        final CompletableFuture<ConnectionStatus> future = new CompletableFuture<>();
        if (apiKey == null || apiKey.isBlank() || !isKeyConfigured()) {
            future.complete(ConnectionStatus.failed("no key, link the server with /react connect <code>"));
            return future;
        }
        final HttpUrl url = apiUrl("/api/v1/auth/check");
        if (url == null) {
            future.complete(ConnectionStatus.failed("invalid inference.endpoint.base-url"));
            return future;
        }
        final Request request = new Request.Builder().url(url).header("Authorization", "Bearer " + apiKey).get().build();
        noRedirectClient().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException exception) {
                future.complete(ConnectionStatus.failed(String.valueOf(exception.getMessage())));
            }
            @Override public void onResponse(Call call, Response response) {
                try (response; ResponseBody body = response.body()) {
                    final Map<String, Object> json = body == null || body.source().request(MAX_RESPONSE_BYTES)
                            ? null : MoshiHolder.RESPONSE_ADAPTER.fromJson(body.string());
                    if (!response.isSuccessful() || json == null) {
                        final Object detail = json == null ? null : json.get("detail");
                        future.complete(ConnectionStatus.failed(detail != null ? String.valueOf(detail) : "HTTP " + response.code()));
                        return;
                    }
                    enabledModels = readEnabledModels(json);
                    future.complete(new ConnectionStatus(String.valueOf(json.get("server")), readModelStates(json), null));
                } catch (Exception exception) {
                    future.complete(ConnectionStatus.failed(String.valueOf(exception.getMessage())));
                }
            }
        });
        return future;
    }

    private static Map<ModelId, Boolean> readModelStates(final Map<String, Object> json) {
        final Map<ModelId, Boolean> states = new EnumMap<>(ModelId.class);
        if (json.get("models") instanceof List<?> items) {
            for (final Object item : items) {
                if (item instanceof Map<?, ?> entry) {
                    final ModelId model = ModelId.fromIdentity(String.valueOf(entry.get("id")));
                    if (model != null) states.put(model, Boolean.TRUE.equals(entry.get("enabled")));
                }
            }
        }
        return states;
    }

    private boolean isKeyConfigured() {
        // The shipped config holds a non-ASCII placeholder until a server is linked.
        return apiKey.matches("[A-Za-z0-9_.:-]+");
    }

    /** The outcome of /react connect; apiKey is null when the code was not accepted. */
    public record ConnectResult(String apiKey, String server, String modelNames, String detail) {
    }

    /** Exchanges a code from the server's page on the site for this server's key. */
    public @NotNull CompletableFuture<ConnectResult> connect(final @NotNull String code) {
        final CompletableFuture<ConnectResult> future = new CompletableFuture<>();
        final HttpUrl url = apiUrl("/api/v1/auth/connect");
        if (url == null) {
            future.completeExceptionally(new IllegalStateException("Invalid inference.endpoint.base-url"));
            return future;
        }
        final String json = MoshiHolder.REQUEST_ADAPTER.toJson(Map.of("code", code));
        final Request request = new Request.Builder().url(url)
                .post(RequestBody.create(json, MediaType.get("application/json; charset=utf-8"))).build();
        noRedirectClient().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException exception) {
                future.completeExceptionally(exception);
            }
            @Override public void onResponse(Call call, Response response) {
                try (response; ResponseBody body = response.body()) {
                    if (body == null || body.source().request(MAX_RESPONSE_BYTES)) {
                        future.complete(new ConnectResult(null, "", "", "HTTP " + response.code()));
                        return;
                    }
                    final Map<String, Object> result = MoshiHolder.RESPONSE_ADAPTER.fromJson(body.string());
                    if (result == null) {
                        future.complete(new ConnectResult(null, "", "", "HTTP " + response.code()));
                    } else if (!response.isSuccessful() || !(result.get("api_key") instanceof String key)) {
                        future.complete(new ConnectResult(null, "", "", String.valueOf(result.get("detail"))));
                    } else {
                        future.complete(new ConnectResult(key, String.valueOf(result.get("server")),
                                readEnabledModels(result).stream().map(ModelId::displayName)
                                        .collect(Collectors.joining(", ")), ""));
                    }
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            }
        });
        return future;
    }

    private HttpUrl apiUrl(final String path) {
        final HttpUrl origin = HttpUrl.parse(baseUrl);
        return origin == null ? null : origin.newBuilder().encodedPath(path).query(null).fragment(null).build();
    }

    /** Never forward the key to a redirect destination. */
    private OkHttpClient noRedirectClient() {
        return client.newBuilder().followRedirects(false).followSslRedirects(false).build();
    }

    public @NotNull Call newCall(
            final @NotNull HttpRequest httpRequest,
            final @NotNull ModelId model
    ) {
        if (!isAcceptingRequests()) throw new IllegalStateException("Inference client is paused or closed");
        final String rawUrl = baseUrl + PATH;
        final HttpUrl parsedUrl = HttpUrl.parse(rawUrl);

        if (parsedUrl == null) {
            throw new IllegalStateException(
                    "Invalid ML inference endpoint URL: '" + rawUrl + "'. " +
                            "Check inference.endpoint.base-url"
            );
        }

        final HttpUrl url = authApplier.applyToUrl(parsedUrl.newBuilder()
                .addQueryParameter("model-family", model.family())
                .addQueryParameter("model-name", model.modelName())
                .build());

        final Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .method(httpRequest.getMethod(), httpRequest.getBody())
                // Several models run side by side, so the request names its own model.
                .header("X-React-Player-Scoped", "true");

        httpRequest.getHeaders().forEach(requestBuilder::header);
        authApplier.applyToHeaders(requestBuilder);

        return this.client.newCall(requestBuilder.build());
    }

    public synchronized void callAsync(
            final @NotNull HttpRequest request,
            final @NotNull ModelId model,
            final @NotNull Callback callback
    ) {
        this.newCall(request, model).enqueue(callback);
    }


    public synchronized void shutdown() {
        closed.set(true);
        suspended.set(true);
        requestEpoch.incrementAndGet();
        this.client.dispatcher().cancelAll();
        try {
            this.client.dispatcher().executorService().shutdown();
        } catch (final Throwable ignored) {
        }

        try {
            this.client.connectionPool().evictAll();
        } catch (final Throwable ignored) {
        }

        try {
            final Cache cache = this.client.cache();

            if (cache != null) {
                cache.close();
            }
        } catch (final Throwable ignored) {
        }
    }

    private void validate(
            final @NotNull Logger logger,
            final @NotNull AuthSettings auth
    ) {
        if (HttpUrl.parse(baseUrl + PATH) == null) {
            logger.warn(
                    "ML inference endpoint '{}{}' is not a valid URL, requests will fail",
                    baseUrl, PATH
            );
        }

        if (!auth.isEnabled() || auth.getType() == AuthType.NONE) {
            return;
        }

        switch (auth.getType()) {
            case HEADER -> warnIfBlank(logger, "auth.header.name", auth.getHeaderName());

            case QUERY -> warnIfBlank(logger, "auth.query.parameter", auth.getQueryParameter());

            case BODY -> warnIfBlank(logger, "auth.body.field", auth.getBodyField());
        }
    }

    private static void warnIfBlank(
            final @NotNull Logger logger,
            final @NotNull String key,
            final String value
    ) {
        if (value == null || value.isBlank()) {
            logger.warn("ML inference config '{}' is blank, requests may be rejected", key);
        }
    }

    private static @NotNull String normalizeUrl(
            final @NotNull String rawUrl
    ) {
        String url = rawUrl.trim();

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }
}
