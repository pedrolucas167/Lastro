package io.github.pedrolucas167.lastro.config;

import io.github.pedrolucas167.lastro.domain.MatchingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }
}
