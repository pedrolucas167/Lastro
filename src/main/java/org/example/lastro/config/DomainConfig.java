package org.example.lastro.config;

import org.example.lastro.domain.MatchingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }
}
