package com.toefly.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Stateless JWT resource server with Keycloak roles; services override it by declaring their own chain. */
@AutoConfiguration(
        beforeName = {
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"
        })
public class ToeflySecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    KeycloakRoleConverter keycloakRoleConverter() {
        return new KeycloakRoleConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    BearerTokenPropagationInterceptor bearerTokenPropagationInterceptor() {
        return new BearerTokenPropagationInterceptor();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HttpSecurity.class)
    static class ResourceServerConfiguration {

        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain toeflyResourceServerFilterChain(HttpSecurity http, KeycloakRoleConverter converter)
                throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(requests -> requests.requestMatchers(
                                    "/actuator/health/**", "/actuator/info", "/v3/api-docs/**")
                            .permitAll()
                            .requestMatchers("/internal/**")
                            .hasRole("SERVICE")
                            .anyRequest()
                            .authenticated())
                    .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                    .httpBasic(basic -> basic.disable())
                    .formLogin(form -> form.disable())
                    .anonymous(Customizer.withDefaults());
            return http.build();
        }
    }
}
