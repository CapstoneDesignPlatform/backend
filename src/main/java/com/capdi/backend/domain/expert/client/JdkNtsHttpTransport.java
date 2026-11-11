package com.capdi.backend.domain.expert.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** No framework request logging/observation interceptors receive the key-bearing URI. */
public class JdkNtsHttpTransport implements NtsHttpTransport, AutoCloseable {
    private final HttpClient client;
    private static final int MAX_RESPONSE = 1024 * 1024;

    public JdkNtsHttpTransport(HttpClient client) { this.client = client; }

    @Override
    public Response post(URI uri, String json, Duration timeout) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(uri).timeout(timeout).header("Content-Type", "application/json")
                .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        var future = client.sendAsync(request, ignored -> new LimitedBody());
        try {
            // The future completes after the body, so slow body delivery is bounded too.
            var response = future.get(timeout.toNanos(), java.util.concurrent.TimeUnit.NANOSECONDS);
            return new Response(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new java.net.http.HttpTimeoutException("NTS request timed out");
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof java.net.http.HttpTimeoutException) {
                throw new java.net.http.HttpTimeoutException("NTS request timed out");
            }
            throw new IOException("NTS request failed");
        } catch (InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }

    static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private java.util.concurrent.Flow.Subscription subscription;
        private long bytes;
        private boolean failed;
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            if (failed) return;
            for (var buffer : buffers) bytes += buffer.remaining();
            if (bytes > MAX_RESPONSE) {
                failed = true;
                subscription.cancel();
                delegate.onError(new IOException("NTS response exceeds limit"));
            } else delegate.onNext(buffers);
        }
        public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        public void onComplete() { if (!failed) delegate.onComplete(); }
    }

    @Override public void close() { client.close(); }
}
