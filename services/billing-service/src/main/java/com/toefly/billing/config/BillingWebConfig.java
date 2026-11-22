package com.toefly.billing.config;

import com.toefly.billing.adapter.in.web.api.AdminBillingApi;
import com.toefly.billing.adapter.in.web.api.AppStoreApi;
import com.toefly.billing.adapter.in.web.api.CheckoutApi;
import com.toefly.billing.adapter.in.web.api.EntitlementsApi;
import com.toefly.billing.adapter.in.web.api.PlanTiersApi;
import com.toefly.billing.adapter.in.web.api.SubscriptionsApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * billing-service.yaml keeps the public paths relative to {@code /api/v1}; the internal paths carry
 * {@code /internal/v1}. Webhooks and the price list need no token: webhooks are protected by their signatures.
 */
@Configuration(proxyBeanMethods = false)
class BillingWebConfig implements WebMvcConfigurer {

    static final String PUBLIC_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                PUBLIC_PREFIX,
                HandlerTypePredicate.forAssignableType(
                        PlanTiersApi.class,
                        CheckoutApi.class,
                        SubscriptionsApi.class,
                        EntitlementsApi.class,
                        AppStoreApi.class,
                        AdminBillingApi.class));
    }

    @Bean
    WebSecurityCustomizer publicBillingEndpoints() {
        return web -> web.ignoring()
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhooks/**")
                .requestMatchers(HttpMethod.GET, "/api/v1/plan-tiers");
    }
}
