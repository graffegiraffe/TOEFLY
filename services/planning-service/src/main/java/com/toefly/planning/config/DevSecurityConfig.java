package com.toefly.planning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

/**
 * {@code /dev/**} without a token, only where the clock can move (profiles local and demo). A customizer, not a
 * second filter chain, so the resource-server chain of toefly-common-security stays in place.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "demo"})
class DevSecurityConfig {

    @Bean
    WebSecurityCustomizer devClockWithoutToken() {
        return web -> web.ignoring().requestMatchers("/dev/**");
    }
}
