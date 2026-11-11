package com.capdi.backend.domain.expert.client;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JdkNtsHttpTransportTest {
    @Test @SuppressWarnings("unchecked")
    void sendsPostJsonWithBoundedTimeoutWithoutAnyRealNetwork() throws Exception {
        var sdk = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(sdk.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        try (var transport = new JdkNtsHttpTransport(sdk)) {
            assertThat(transport.post(URI.create("https://example.test/status"),"{}",Duration.ofSeconds(2)).status()).isEqualTo(200);
            var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
            verify(sdk).sendAsync(request.capture(),any(HttpResponse.BodyHandler.class));
            assertThat(request.getValue().method()).isEqualTo("POST");
            assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(2));
            assertThat(request.getValue().headers().firstValue("Content-Type")).contains("application/json");
        }
        verify(sdk).close();
    }
    @Test @SuppressWarnings("unchecked")
    void slowBodyFutureTimesOutAndIsCancelled() {
        var sdk = mock(HttpClient.class);
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(sdk.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        try (var transport = new JdkNtsHttpTransport(sdk)) {
            assertThatThrownBy(() -> transport.post(URI.create("https://example.test/status"),"{}",Duration.ofMillis(5)))
                    .isInstanceOf(HttpTimeoutException.class).hasMessageNotContaining("example.test");
            assertThat(pending.isCancelled()).isTrue();
        }
    }
    @Test void oversizedBodyIsCancelledBeforeUnboundedAccumulation() {
        var body = new JdkNtsHttpTransport.LimitedBody();
        var subscription = mock(java.util.concurrent.Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(java.util.List.of(java.nio.ByteBuffer.allocate(1024*1024+1)));
        assertThatThrownBy(() -> body.getBody().toCompletableFuture().join())
                .hasCauseInstanceOf(java.io.IOException.class);
        verify(subscription).cancel();
    }
}
