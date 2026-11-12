package com.capdi.backend.domain.expert.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ExpertSearchConfig {

    @Bean
    public Clock expertSearchClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
