package com.toefly.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

/** The local checkout page of the Stripe emulator, only in profiles local and demo. */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "demo"})
class DevSecurityConfig {

    @Bean
    WebSecurityCustomizer devCheckoutWithoutToken() {
        return web -> web.ignoring().requestMatchers("/dev/**");
    }
}
