package com.g4vrk.react.inference.server;

import com.g4vrk.react.inference.auth.AuthApplier;
import com.g4vrk.react.inference.auth.AuthSettings;
import com.g4vrk.react.inference.auth.type.AuthType;
import com.g4vrk.react.inference.http.LimitedBodyHandler;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.json.InferenceJsonCodec;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.server.settings.InferenceEndpointSettings;
import com.g4vrk.react.inference.server.settings.InferenceSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Getter
public final class InferenceServer {

    private static final String PATH = "/api/v2/inference/";
    private static final long MODEL_REFRESH_MILLIS = 60_000L;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Method HTTP_CLIENT_SHUTDOWN_NOW = findShutdownMethod();

    private final HttpClient client;
    // Never forward the key to a redirect destination.
    private final HttpClient noRedirectClient;
    private final AuthApplier authApplier;

    private final String baseUrl;
    private final InferenceEndpointSettings endpoint;
    private final Duration callTimeout;

    private final boolean enabled;
    private final String authBodyToken;
    private final Logger logger;
    private final String apiKey;
    private final AtomicLong nextModelRefresh = new AtomicLong();
    private volatile Set<ModelId> enabledModels = Set.of();

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong requestEpoch = new AtomicLong();
    private final Set<CompletableFuture<?>> inFlight = ConcurrentHashMap.newKeySet();

    public long requestEpoch() { return requestEpoch.get(); }

    /** Serialize the FINAL publication with shutdown, including callbacks on entity schedulers. */
    public synchronized void publishIfCurrent(long epoch, Runnable publication) {
        if (isAcceptingRequests() && requestEpoch.get() == epoch) publication.run();
    }

    public boolean isAcceptingRequests() {
        return enabled && !closed.get();
    }

    public InferenceServer(
            final @NotNull Logger logger,
            final @NotNull InferenceSettings settings
    ) {
        this.client = settings.buildClient();
        this.noRedirectClient = settings.buildClient(HttpClient.Redirect.NEVER);
        this.callTimeout = settings.getCallTimeout();
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
        final URI url = apiUrl("/api/v1/auth/check");
        if (url == null) return;
        try {
            sendAccountRequest(java.net.http.HttpRequest.newBuilder(url)
                    .header("Authorization", "Bearer " + apiKey).GET()).whenComplete((response, failure) -> {
                if (failure != null) {
                    // A network failure keeps the last known models instead of stopping every check.
                    logger.debug("Could not refresh the enabled models", failure);
                    return;
                }
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    enabledModels = Set.of();
                    logger.warn("The server key was rejected: link it again with /react connect <code>");
                    return;
                }
                if (!isSuccessful(response)) return;
                try {
                    enabledModels = readEnabledModels(readJson(response));
                } catch (RuntimeException exception) {
                    logger.debug("Could not read the enabled models", exception);
                }
            });
        } catch (RuntimeException exception) {
            logger.debug("Could not refresh the enabled models", exception);
        }
    }

    private static Set<ModelId> readEnabledModels(final Map<String, Object> json) {
        final EnumSet<ModelId> models = EnumSet.noneOf(ModelId.class);
        readModelStates(json).forEach((model, enabled) -> {
            if (enabled) models.add(model);
        });
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
        if (apiKey == null || apiKey.isBlank() || !isKeyConfigured()) {
            return CompletableFuture.completedFuture(
                    ConnectionStatus.failed("no key, link the server with /react connect <code>"));
        }
        final URI url = apiUrl("/api/v1/auth/check");
        if (url == null) {
            return CompletableFuture.completedFuture(ConnectionStatus.failed("invalid inference.endpoint.base-url"));
        }
        return sendAccountRequest(java.net.http.HttpRequest.newBuilder(url)
                .header("Authorization", "Bearer " + apiKey).GET()).handle((response, failure) -> {
            if (failure != null) {
                return ConnectionStatus.failed(String.valueOf(failure.getMessage()));
            }
            try {
                final Map<String, Object> json = readJson(response);
                if (!isSuccessful(response)) {
                    final Object detail = json.get("detail");
                    return ConnectionStatus.failed(detail != null ? String.valueOf(detail) : "HTTP " + response.statusCode());
                }
                enabledModels = readEnabledModels(json);
                return new ConnectionStatus(String.valueOf(json.get("server")), readModelStates(json), null);
            } catch (RuntimeException exception) {
                return ConnectionStatus.failed(isSuccessful(response)
                        ? String.valueOf(exception.getMessage()) : "HTTP " + response.statusCode());
            }
        });
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
        final URI url = apiUrl("/api/v1/auth/connect");
        if (url == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Invalid inference.endpoint.base-url"));
        }
        final String json = InferenceJsonCodec.writeObject(Map.of("code", code));
        return sendAccountRequest(java.net.http.HttpRequest.newBuilder(url)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)))
                .thenApply(response -> {
                    final Map<String, Object> result;
                    try {
                        result = readJson(response);
                    } catch (RuntimeException exception) {
                        return new ConnectResult(null, "", "", "HTTP " + response.statusCode());
                    }
                    if (!isSuccessful(response) || !(result.get("api_key") instanceof String key)) {
                        return new ConnectResult(null, "", "", String.valueOf(result.get("detail")));
                    }
                    return new ConnectResult(key, String.valueOf(result.get("server")),
                            readEnabledModels(result).stream().map(ModelId::displayName)
                                    .collect(Collectors.joining(", ")), "");
                });
    }

    private @NotNull CompletableFuture<HttpResponse<byte[]>> sendAccountRequest(
            final java.net.http.HttpRequest.@NotNull Builder builder
    ) {
        if (!callTimeout.isZero() && !callTimeout.isNegative()) builder.timeout(callTimeout);
        return noRedirectClient.sendAsync(builder.build(), new LimitedBodyHandler(MAX_RESPONSE_BYTES));
    }

    private static Map<String, Object> readJson(final HttpResponse<byte[]> response) {
        return InferenceJsonCodec.readObject(new String(response.body(), StandardCharsets.UTF_8));
    }

    private static boolean isSuccessful(final HttpResponse<?> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private @Nullable URI apiUrl(final String path) {
        try {
            final URI origin = URI.create(baseUrl);
            return isHttpUrl(origin) ? new URI(origin.getScheme(), origin.getRawAuthority(), path, null, null) : null;
        } catch (final IllegalArgumentException | URISyntaxException ignored) {
            return null;
        }
    }

    /** Sends one model request; its body is capped and the call is cancelled when the server shuts down. */
    public synchronized @NotNull CompletableFuture<HttpResponse<byte[]>> callAsync(
            final @NotNull HttpRequest httpRequest,
            final @NotNull ModelId model
    ) {
        if (!isAcceptingRequests()) throw new IllegalStateException("Inference client is closed");
        final String rawUrl = baseUrl + PATH
                + "?model-family=" + URLEncoder.encode(model.family(), StandardCharsets.UTF_8)
                + "&model-name=" + URLEncoder.encode(model.modelName(), StandardCharsets.UTF_8);
        final URI url;
        try {
            url = URI.create(rawUrl);
        } catch (final IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid ML inference endpoint URL: '" + rawUrl + "'. Check inference.endpoint.base-url", ex);
        }
        if (!isHttpUrl(url)) {
            throw new IllegalStateException(
                    "Invalid ML inference endpoint URL: '" + rawUrl + "'. Check inference.endpoint.base-url");
        }

        final java.net.http.HttpRequest.Builder requestBuilder =
                java.net.http.HttpRequest.newBuilder(authApplier.applyToUrl(url))
                        .method(httpRequest.getMethod(),
                                java.net.http.HttpRequest.BodyPublishers.ofByteArray(httpRequest.getBody()))
                        .header("Content-Type", httpRequest.getContentType())
                        // Several models run side by side, so the request names its own model.
                        .header("X-React-Player-Scoped", "true");
        if (!callTimeout.isZero() && !callTimeout.isNegative()) requestBuilder.timeout(callTimeout);

        httpRequest.getHeaders().forEach(requestBuilder::header);
        authApplier.applyToHeaders(requestBuilder);

        final CompletableFuture<HttpResponse<byte[]>> call =
                client.sendAsync(requestBuilder.build(), new LimitedBodyHandler(MAX_RESPONSE_BYTES));
        inFlight.add(call);
        call.whenComplete((response, failure) -> inFlight.remove(call));
        return call;
    }

    public synchronized void shutdown() {
        closed.set(true);
        requestEpoch.incrementAndGet();
        inFlight.forEach(call -> call.cancel(true));
        inFlight.clear();
        if (HTTP_CLIENT_SHUTDOWN_NOW == null) return;
        for (final HttpClient httpClient : List.of(client, noRedirectClient)) {
            try {
                HTTP_CLIENT_SHUTDOWN_NOW.invoke(httpClient);
            } catch (final IllegalAccessException | InvocationTargetException ex) {
                logger.debug("Could not shut down the inference HTTP client", ex);
            }
        }
    }

    private static Method findShutdownMethod() {
        try {
            return HttpClient.class.getMethod("shutdownNow");
        } catch (final NoSuchMethodException ignored) {
            return null;
        }
    }

    private void validate(
            final @NotNull Logger logger,
            final @NotNull AuthSettings auth
    ) {
        if (apiUrl(PATH) == null) {
            logger.warn(
                    "ML inference endpoint '{}{}' is not a valid HTTP URL, requests will fail",
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

    private static boolean isHttpUrl(final @NotNull URI uri) {
        final String scheme = uri.getScheme();

        return uri.getHost() != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
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
