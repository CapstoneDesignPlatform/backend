package com.capdi.backend.domain.payment.gateway.portone;

import java.net.http.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import com.capdi.backend.domain.payment.gateway.PaymentGateway.GatewayException;
import com.capdi.backend.domain.payment.entity.PaymentProcessingFailure;

/** Fixed TLS origin, bounded body, no redirects, no application retry, no request logging. */
public interface PortOneTransport {
    record Reply(int status,byte[] body) {
        @Override public String toString(){return "Reply[REDACTED]";}
    }
    Reply get(String paymentId,String storeId,String secret,Duration timeout);
    /** Store-scoped, first-page, one-item TEST/V2 list query. */
    default Reply getPayments(String storeId,String secret,Duration timeout) {
        throw new GatewayException(PaymentProcessingFailure.UNSUPPORTED);
    }
    final class Jdk implements PortOneTransport {
        private final PortOneProxySelector proxySelector;
        private final HttpClient client;
        /** Disabled-mode compatibility constructor. Requests fail closed without a proxy. */
        public Jdk(){this(null);}
        public Jdk(PortOneProxySelector proxySelector) {
            this.proxySelector=proxySelector;
            var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER);
            if(proxySelector!=null)builder.proxy(proxySelector);
            this.client=builder.build();
        }
        public Reply get(String paymentId,String storeId,String secret,Duration timeout) {
            if(!PortOneSettings.identifier(paymentId) || !PortOneSettings.identifier(storeId))
                throw new GatewayException(PaymentProcessingFailure.RESPONSE_MISMATCH);
            return send(URI.create("https://api.portone.io/payments/"+paymentId+"?storeId="+storeId),secret,timeout);
        }
        @Override public Reply getPayments(String storeId,String secret,Duration timeout) {
            if(!PortOneSettings.identifier(storeId))
                throw new GatewayException(PaymentProcessingFailure.RESPONSE_MISMATCH);
            return send(paymentsListUri(storeId),secret,timeout);
        }
        static URI paymentsListUri(String storeId) {
            if(!PortOneSettings.identifier(storeId))
                throw new GatewayException(PaymentProcessingFailure.RESPONSE_MISMATCH);
            // PortOne V2 GET /payments accepts GetPaymentsBody as JSON in the requestBody query parameter.
            // Hard-coded filters keep this diagnostic limited to one page, one item, this Store, TEST, V2.
            String requestBody="{\"page\":{\"number\":0,\"size\":1},\"filter\":{\"storeId\":\""+storeId
                    +"\",\"isTest\":true,\"version\":\"V2\"}}";
            return URI.create("https://api.portone.io/payments?requestBody="
                    +URLEncoder.encode(requestBody,StandardCharsets.UTF_8));
        }
        private Reply send(URI uri,String secret,Duration timeout) {
            if(proxySelector==null || secret==null || secret.isBlank() || timeout==null || timeout.isNegative() || timeout.isZero()
                    || timeout.compareTo(Duration.ofSeconds(30))>0)
                throw new GatewayException(PaymentProcessingFailure.NETWORK);
            var request=HttpRequest.newBuilder(uri)
                    .timeout(timeout).header("Authorization","PortOne "+secret).header("Accept","application/json").GET().build();
            try {
                var response=client.send(request, info->new LimitedBody());
                return new Reply(response.statusCode(),response.body());
            } catch(HttpTimeoutException e){throw new GatewayException(PaymentProcessingFailure.TIMEOUT);}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new GatewayException(PaymentProcessingFailure.NETWORK);}
            catch(Exception e){throw new GatewayException(PaymentProcessingFailure.NETWORK);}
        }
        static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
            final CompletableFuture<byte[]> result=new CompletableFuture<>();
            final ByteArrayOutputStream out=new ByteArrayOutputStream();
            Flow.Subscription subscription;
            public CompletionStage<byte[]> getBody(){return result;}
            public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
            public void onNext(List<ByteBuffer> chunks){
                for(var chunk:chunks) {
                    if(chunk.remaining()>65536-out.size()) {
                        subscription.cancel();result.completeExceptionally(new IllegalStateException("Response limit"));return;
                    }
                    byte[] bytes=new byte[chunk.remaining()];chunk.get(bytes);out.writeBytes(bytes);
                }
                subscription.request(1);
            }
            public void onError(Throwable t){result.completeExceptionally(new IllegalStateException("Transport failure"));}
            public void onComplete(){result.complete(out.toByteArray());}
        }
    }
}
