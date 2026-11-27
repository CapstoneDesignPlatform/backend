package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.entity.PaymentProcessingFailure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Durable inbox retry policy. The attempt limit includes the initial lookup. */
@Component
public class PaymentWebhookRetryPolicy {
    private final int maxAttempts;
    private final long baseDelaySeconds;
    private final long maxDelaySeconds;
    private final Duration lease;

    public PaymentWebhookRetryPolicy(
            @Value("${payment.webhook.consumer.max-attempts:5}") int maxAttempts,
            @Value("${payment.webhook.consumer.base-delay-seconds:30}") long baseDelaySeconds,
            @Value("${payment.webhook.consumer.max-delay-seconds:3600}") long maxDelaySeconds,
            @Value("${payment.webhook.consumer.lease-seconds:60}") long leaseSeconds) {
        if (maxAttempts < 1 || maxAttempts > 20 || baseDelaySeconds < 1
                || maxDelaySeconds < baseDelaySeconds || maxDelaySeconds > 86_400
                || leaseSeconds < 31 || leaseSeconds > 600) {
            throw new IllegalArgumentException("Invalid payment webhook retry policy");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelaySeconds = baseDelaySeconds;
        this.maxDelaySeconds = maxDelaySeconds;
        this.lease = Duration.ofSeconds(leaseSeconds);
    }

    public int maxAttempts() { return maxAttempts; }
    public Duration lease() { return lease; }

    public Duration delayAfterAttempt(int attemptCount) {
        int shift = Math.max(0, Math.min(30, attemptCount - 1));
        long delay = baseDelaySeconds;
        for (int i = 0; i < shift && delay < maxDelaySeconds; i++) {
            delay = Math.min(maxDelaySeconds, delay > maxDelaySeconds / 2 ? maxDelaySeconds : delay * 2);
        }
        return Duration.ofSeconds(Math.min(delay, maxDelaySeconds));
    }

    public PaymentWebhookEventFailure classify(PaymentProcessingFailure failure) {
        if (failure == PaymentProcessingFailure.TIMEOUT || failure == PaymentProcessingFailure.NETWORK
                || failure == PaymentProcessingFailure.PROVIDER_ERROR
                || failure == PaymentProcessingFailure.TRANSACTION_CONFLICT
                || failure == PaymentProcessingFailure.STORAGE_FAILURE
                || failure == PaymentProcessingFailure.PROCESS_INTERRUPTED
                || failure == PaymentProcessingFailure.DISABLED) {
            return PaymentWebhookEventFailure.TRANSIENT;
        }
        return PaymentWebhookEventFailure.PERMANENT;
    }

    public enum PaymentWebhookEventFailure { TRANSIENT, PERMANENT }
}
