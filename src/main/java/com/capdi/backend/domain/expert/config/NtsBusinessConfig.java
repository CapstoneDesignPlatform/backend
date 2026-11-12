package com.capdi.backend.domain.expert.config;

import com.capdi.backend.domain.expert.client.JdkNtsHttpTransport;
import com.capdi.backend.domain.expert.client.NtsHttpTransport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(NtsBusinessProperties.class)
public class NtsBusinessConfig {
    @Bean
    public NtsHttpTransport ntsHttpTransport(NtsBusinessProperties properties) {
        if (properties.getConnectTimeout() == null || properties.getConnectTimeout().isNegative()
                || properties.getConnectTimeout().isZero() || properties.getResponseTimeout() == null
                || properties.getResponseTimeout().isNegative() || properties.getResponseTimeout().isZero()) {
            throw new IllegalStateException("Invalid NTS timeout configuration");
        }
        return new JdkNtsHttpTransport(HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
}
