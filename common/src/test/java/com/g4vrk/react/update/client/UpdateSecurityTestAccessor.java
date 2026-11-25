package com.g4vrk.react.update.client;

import org.jetbrains.annotations.NotNull;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Exposes package-private security primitives of {@code update.client} to tests without weakening visibility.
 */
public final class UpdateSecurityTestAccessor {

    private UpdateSecurityTestAccessor() {

        throw new UnsupportedOperationException("Utility class");

    }

    public static void verifySignature(
            final byte @NotNull [] data,
            final byte @NotNull [] signatureText
    ) throws ReleaseClient.VerificationException {

        Ed25519SignatureVerifier.verify(data, signatureText, "checksums.txt");

    }

    public static HttpResponse.@NotNull BodySubscriber<byte[]> limitedByteSubscriber(
            final @NotNull CompletableFuture<byte[]> downstream,
            final long limit
    ) {

        final HttpResponse.BodySubscriber<byte[]> base = HttpResponse.BodySubscribers.ofByteArray();
        base.getBody().whenComplete((bytes, error) -> {
            if (error != null) {
                downstream.completeExceptionally(error);
            } else {
                downstream.complete(bytes);
            }
        });
        return new LimitedBodySubscriber<>(base, limit);

    }

}
