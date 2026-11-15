package com.toefly.aigateway.config;

import com.toefly.common.security.KeycloakRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Service tokens for {@code /internal/**}; the admin section for ADMIN, and eval reports also for the CI service
 * token (docs/05-api-contracts.md §6.15).
 */
@Configuration(proxyBeanMethods = false)
class AiGatewaySecurityConfig {

    @Bean
    SecurityFilterChain aiGatewayFilterChain(HttpSecurity http, KeycloakRoleConverter converter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests -> requests.requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**")
                                .permitAll()
                                .requestMatchers("/internal/**")
                                .hasRole("SERVICE")
                                .requestMatchers(HttpMethod.POST, "/api/v1/admin/ai/eval-runs")
                                .hasAnyRole("ADMIN", "SERVICE")
                                .requestMatchers("/api/v1/admin/**")
                                .hasRole("ADMIN")
                                .anyRequest()
                                .denyAll())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .anonymous(Customizer.withDefaults());
        return http.build();
    }
}
