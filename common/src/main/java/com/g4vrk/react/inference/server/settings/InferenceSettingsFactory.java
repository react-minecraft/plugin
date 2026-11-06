package com.g4vrk.react.inference.server.settings;

import com.g4vrk.react.inference.auth.AuthSettings;
import com.g4vrk.react.inference.auth.type.AuthType;
import com.g4vrk.react.parse.time.TimeParser;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import java.time.Duration;

public class InferenceSettingsFactory {

    private final Duration defaultTimeout = Duration.ofSeconds(10);

    public @NotNull InferenceSettings create(
            final @NotNull ConfigurationNode root
    ) {
        final boolean enabled = root.node("enabled").getBoolean(true);

        final ConfigurationNode inferenceNode = root.node("inference");

        final InferenceEndpointSettings endpoint = parseEndpoint(inferenceNode.node("endpoint"));

        final AuthSettings auth = parseAuth(root.node("auth"));

        final ConfigurationNode clientNode = root.node("client");

        return new InferenceSettings(
                parseTimeout(clientNode, "connect-timeout"),
                parseTimeout(clientNode, "call-timeout"),
                enabled,
                endpoint,
                auth
        );
    }

    private @NotNull InferenceEndpointSettings parseEndpoint(
            final @NotNull ConfigurationNode endpointNode
    ) {
        return new InferenceEndpointSettings(endpointNode.node("base-url").getString("http://localhost:8080"));
    }

    private @NotNull AuthSettings parseAuth(
            final @NotNull ConfigurationNode authNode
    ) {
        final boolean enabled = authNode.node("enabled").getBoolean(false);
        final AuthType type = AuthType.safelyMatch(authNode.node("type").getString("none"), AuthType.NONE);
        String value = authNode.node("value").getString();
        if (value == null) {
            // Accept older server-generated configs with a value under the selected method.
            value = type == AuthType.BEARER ? authNode.node("bearer", "token").getString("")
                    : authNode.node(type.name().toLowerCase(java.util.Locale.ROOT), "value").getString("");
        }

        return new AuthSettings(
                enabled,
                type,
                value,
                authNode.node("header", "name").getString("X-Subscription-Token"),
                authNode.node("query", "parameter").getString("token"),
                authNode.node("body", "field").getString("token")
        );
    }

    private @NotNull Duration parseTimeout(
            final @NotNull ConfigurationNode clientNode,
            final @NotNull String key
    ) {
        return TimeParser.parseDurationOrDefault(
                clientNode.node(key).getString("10s"),
                defaultTimeout
        );
    }

}
