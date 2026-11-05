package com.toefly.apigateway.config;

import com.toefly.apigateway.application.service.RateLimitService.RateLimitPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    @Bean
    RateLimitPolicy rateLimitPolicy(GatewayProperties properties) {
        return new RateLimitPolicy(properties.rateLimits());
    }
}
