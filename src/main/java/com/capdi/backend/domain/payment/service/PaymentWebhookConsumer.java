package com.capdi.backend.domain.payment.service;

import lombok.RequiredArgsConstructor;
import com.capdi.backend.domain.payment.repository.PaymentWebhookEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;

/** Opt-in inbox poller. Claims are serialized by the existing row lock and lease. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="payment.webhook.consumer.enabled",havingValue="true")
public class PaymentWebhookConsumer {
    private final PaymentWebhookEventRepository events;
    private final PaymentWebhookService service;
    private final Clock clock;
    private final org.springframework.core.env.Environment environment;

    @Scheduled(fixedDelayString="${payment.webhook.consumer.delay-ms:5000}",initialDelayString="${payment.webhook.consumer.delay-ms:5000}")
    public void poll() {
        int batch=environment.getProperty("payment.webhook.consumer.batch-size",Integer.class,10);
        if(batch<1 || batch>100)return;
        for(Long id:events.findDueIds(clock.instant(),batch)) {
            try { service.process(id); } catch(RuntimeException ignored) { /* durable inbox remains retryable */ }
        }
    }
}
