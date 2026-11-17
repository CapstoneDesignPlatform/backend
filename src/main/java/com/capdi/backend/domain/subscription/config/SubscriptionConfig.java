package com.capdi.backend.domain.subscription.config;

import com.capdi.backend.domain.subscription.exception.SubscriptionException;
import com.capdi.backend.domain.subscription.gateway.SubscriptionPgClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;
import java.time.Clock;

@Configuration
public class SubscriptionConfig {
    @Bean("subscriptionClock")
    Clock subscriptionClock() { return Clock.systemUTC(); }

    @Bean
    @ConditionalOnMissingBean(SubscriptionPgClient.class)
    SubscriptionPgClient disabledSubscriptionPgClient() {
        return new SubscriptionPgClient() {
            public String provider() { return "DISABLED"; }
            public boolean available() { return false; }
            public Registration register(String token, String customer, String registration) { throw SubscriptionException.unavailable(); }
            public Registration findRegistration(String customer, String registration) { throw SubscriptionException.unavailable(); }
            public Charge charge(String key, String customer, String order, BigDecimal amount, String currency) { throw SubscriptionException.unavailable(); }
            public Charge findCharge(String customer, String order) { throw SubscriptionException.unavailable(); }
        };
    }
}
