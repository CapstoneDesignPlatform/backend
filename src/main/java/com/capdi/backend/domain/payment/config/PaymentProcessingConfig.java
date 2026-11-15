package com.capdi.backend.domain.payment.config;

import com.capdi.backend.domain.payment.entity.PaymentEnvironment;
import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration
public class PaymentProcessingConfig {
    @Bean @ConditionalOnMissingBean(Clock.class)
    Clock paymentClock() { return Clock.systemUTC(); }
    @Bean @ConditionalOnMissingBean(PaymentGateway.class)
    PaymentGateway paymentGateway(org.springframework.core.env.Environment env,Clock clock,
            org.springframework.beans.factory.ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapper) {
        var settings=new com.capdi.backend.domain.payment.gateway.portone.PortOneSettings(
            env.getProperty("payment.portone.enabled",Boolean.class,false),
            env.getProperty("payment.portone.sandbox-approved",Boolean.class,false),
            env.getProperty("payment.portone.environment","DISABLED"),
            env.getProperty("payment.portone.merchant-id"),env.getProperty("payment.portone.store-id"),
            env.getProperty("payment.portone.channel-key"),env.getProperty("payment.portone.api-secret"),
            env.getProperty("payment.portone.webhook-secret"),
            java.time.Duration.ofMillis(env.getProperty("payment.portone.timeout-ms",Long.class,10000L)));
        return new com.capdi.backend.domain.payment.gateway.portone.PortOneV2Gateway(settings,
            new com.capdi.backend.domain.payment.gateway.portone.PortOneTransport.Jdk(),mapper.getIfAvailable(com.fasterxml.jackson.databind.ObjectMapper::new),clock);
    }
}
