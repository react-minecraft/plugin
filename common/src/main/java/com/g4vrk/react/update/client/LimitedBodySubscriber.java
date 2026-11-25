package com.g4vrk.react.update.client;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Wraps an HTTP body subscriber and aborts once received bytes exceed a hard limit.
 *
 * @param <T> the decoded response body type
 */
final class LimitedBodySubscriber<T> implements HttpResponse.BodySubscriber<T> {

    private final HttpResponse.BodySubscriber<T> delegate;
    private final long limit;
    private long received;
    private boolean done;
    private Flow.Subscription subscription;

    LimitedBodySubscriber(
            final HttpResponse.@NotNull BodySubscriber<T> delegate,
            final long limit
    ) {

        this.delegate = delegate;
        this.limit = limit;

    }

    @Override
    public @NotNull CompletionStage<T> getBody() {

        return delegate.getBody();

    }

    @Override
    public void onSubscribe(
            final Flow.@NotNull Subscription subscription
    ) {

        this.subscription = subscription;
        delegate.onSubscribe(subscription);

    }

    @Override
    public void onNext(
            final @NotNull List<ByteBuffer> buffers
    ) {

        if (done) {
            return;
        }
        for (final ByteBuffer buffer : buffers) {
            received += buffer.remaining();
            if (received > limit) {
                done = true;
                subscription.cancel();
                delegate.onError(new IOException("HTTP response exceeds size limit"));
                return;
            }
        }
        delegate.onNext(buffers);

    }

    @Override
    public void onError(
            final @NotNull Throwable failure
    ) {

        if (done) {
            return;
        }
        done = true;
        delegate.onError(failure);

    }

    @Override
    public void onComplete() {

        if (done) {
            return;
        }
        done = true;
        delegate.onComplete();

    }

}
