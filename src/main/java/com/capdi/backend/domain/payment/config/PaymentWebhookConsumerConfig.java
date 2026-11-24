package com.capdi.backend.domain.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="payment.webhook.consumer.enabled",havingValue="true")
public class PaymentWebhookConsumerConfig { }
