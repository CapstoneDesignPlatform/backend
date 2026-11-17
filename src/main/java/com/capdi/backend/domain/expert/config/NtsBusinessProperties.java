package com.capdi.backend.domain.expert.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties("nts.business")
public class NtsBusinessProperties {
    private boolean enabled = false;
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration responseTimeout = Duration.ofSeconds(8);
}
