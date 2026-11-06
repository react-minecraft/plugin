package com.g4vrk.react.inference.http.model;

import java.util.Map;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

@Value
public class HttpRequest {
    @NotNull String method;
    byte @NotNull [] body;
    @NotNull String contentType;
    @NotNull Map<String, String> headers;

    public HttpRequest(String method, byte[] body, String contentType) {
        this(method, body, contentType, Map.of());
    }

    public HttpRequest(String method, byte[] body, String contentType, Map<String, String> headers) {
        this.method = method;
        this.body = body;
        this.contentType = contentType;
        this.headers = Map.copyOf(headers);
    }
}
