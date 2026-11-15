package com.toefly.aigateway.config;

import com.toefly.aigateway.adapter.in.web.api.AdminAiApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ai-gateway.yaml keeps the admin paths relative to the server {@code /api/v1} (api-gateway publishes them as
 * {@code /api/v1/admin/ai/**}); the internal paths carry {@code /internal} themselves.
 */
@Configuration(proxyBeanMethods = false)
class AiGatewayWebConfig implements WebMvcConfigurer {

    static final String PUBLIC_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(PUBLIC_PREFIX, HandlerTypePredicate.forAssignableType(AdminAiApi.class));
    }
}
