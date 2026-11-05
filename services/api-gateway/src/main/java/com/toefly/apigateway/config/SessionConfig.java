package com.toefly.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisIndexedWebSession;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

/**
 * Web sessions in Valkey ({@code spring:session:*}) indexed by user, so all sessions of a user can be ended at once.
 * The cookie is {@code HttpOnly; Secure; SameSite=Strict} (ADR-0023); 14 days of inactivity end a session.
 */
@Configuration(proxyBeanMethods = false)
@EnableRedisIndexedWebSession(maxInactiveIntervalInSeconds = 1_209_600)
public class SessionConfig {

    @Bean
    WebSessionIdResolver webSessionIdResolver(GatewayProperties properties) {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName(properties.session().cookieName());
        resolver.addCookieInitializer(cookie -> cookie.httpOnly(true)
                .secure(properties.session().secureCookie())
                .sameSite("Strict")
                .path("/"));
        return resolver;
    }
}
