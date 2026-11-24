package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.entity.PaymentEnvironment;
import com.capdi.backend.domain.payment.entity.PaymentProcessingFailure;
import com.capdi.backend.domain.payment.entity.PaymentWebhookEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentWebhookRetryPolicyTest {
    private final PaymentWebhookRetryPolicy policy = new PaymentWebhookRetryPolicy(5, 30, 3600, 60);

    @Test void retryDelayUsesCappedExponentialBackoff() {
        assertThat(policy.delayAfterAttempt(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayAfterAttempt(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.delayAfterAttempt(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(policy.delayAfterAttempt(4)).isEqualTo(Duration.ofSeconds(240));
        assertThat(policy.delayAfterAttempt(8)).isEqualTo(Duration.ofSeconds(3600));
    }

    @Test void separatesTransientLookupFailureFromPermanentProofMismatch() {
        assertThat(policy.classify(PaymentProcessingFailure.TIMEOUT))
                .isEqualTo(PaymentWebhookRetryPolicy.PaymentWebhookEventFailure.TRANSIENT);
        assertThat(policy.classify(PaymentProcessingFailure.NETWORK))
                .isEqualTo(PaymentWebhookRetryPolicy.PaymentWebhookEventFailure.TRANSIENT);
        assertThat(policy.classify(PaymentProcessingFailure.RESPONSE_MISMATCH))
                .isEqualTo(PaymentWebhookRetryPolicy.PaymentWebhookEventFailure.PERMANENT);
        assertThat(policy.classify(PaymentProcessingFailure.INCONSISTENT_STATE))
                .isEqualTo(PaymentWebhookRetryPolicy.PaymentWebhookEventFailure.PERMANENT);
    }

    @Test void invalidPolicyFailsClosed() {
        assertThatThrownBy(() -> new PaymentWebhookRetryPolicy(0, 30, 3600, 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentWebhookRetryPolicy(5, 30, 20, 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentWebhookRetryPolicy(5, 30, 3600, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void defaultInboxLeaseExceedsPortOneMaximumRequestTimeout() {
        assertThat(policy.lease()).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.lease()).isGreaterThan(Duration.ofSeconds(30));
    }

    @Test void dueTimeAndMaximumAttemptsMoveEventToDurableDeadLetter() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var event = PaymentWebhookEvent.receive(2L, "PORTONE_V2", "merchant-test", PaymentEnvironment.TEST,
                "event-hash", "payload-hash", now);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getNextAttemptAt()).isEqualTo(now);
        assertThat(event.isDeadLetter()).isFalse();

        for (int attempt = 1; attempt <= 5; attempt++) {
            String token = "token-" + attempt;
            assertThat(event.claim(token, now, policy.lease(), policy.maxAttempts())).isTrue();
            assertThat(event.getAttemptCount()).isEqualTo(attempt);
            assertThat(event.finish(token, now.plusSeconds(1), PaymentWebhookEvent.Outcome.TRANSIENT_FAILURE,
                    "TIMEOUT", policy.maxAttempts(), policy.delayAfterAttempt(attempt))).isTrue();
            if (attempt < 5) {
                assertThat(event.isDeadLetter()).isFalse();
                assertThat(event.claim("early", event.getNextAttemptAt().minusMillis(1), policy.lease(),
                        policy.maxAttempts())).isFalse();
                now = event.getNextAttemptAt();
            }
        }
        assertThat(event.isDeadLetter()).isTrue();
        assertThat(event.getAttemptCount()).isEqualTo(5);
        assertThat(event.getLastFailure()).isEqualTo("TIMEOUT");
        assertThat(event.claim("dead", now.plusSeconds(3600), policy.lease(), policy.maxAttempts())).isFalse();
    }

    @Test void permanentFailureDeadLettersWithoutRetryAndStaleTokenCannotFinish() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var event = PaymentWebhookEvent.receive(3L, "PORTONE_V2", "merchant-test", PaymentEnvironment.TEST,
                "event-hash", "payload-hash", now);
        assertThat(event.claim("owner", now, policy.lease(), policy.maxAttempts())).isTrue();
        assertThat(event.finish("stale", now.plusSeconds(1), PaymentWebhookEvent.Outcome.PERMANENT_FAILURE,
                "RESPONSE_MISMATCH", policy.maxAttempts(), Duration.ZERO)).isFalse();
        assertThat(event.finish("owner", now.plusSeconds(1), PaymentWebhookEvent.Outcome.PERMANENT_FAILURE,
                "RESPONSE_MISMATCH", policy.maxAttempts(), Duration.ZERO)).isTrue();
        assertThat(event.isDeadLetter()).isTrue();
        assertThat(event.getLastFailure()).isEqualTo("RESPONSE_MISMATCH");
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }
}
