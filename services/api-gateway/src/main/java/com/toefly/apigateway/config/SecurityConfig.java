package com.toefly.apigateway.config;

import com.toefly.apigateway.adapter.in.web.BffAuthenticationHandlers;
import com.toefly.apigateway.adapter.in.web.KeycloakOidcUserService;
import com.toefly.apigateway.adapter.in.web.ProblemSecurityHandlers;
import com.toefly.apigateway.adapter.in.web.RegistrationAwareAuthorizationRequestResolver;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * One chain for both kinds of callers (docs/10-security-privacy.md §4.2): the web app signs in through the BFF and
 * sends the session cookie, iOS and services send a Bearer token, which wins when both are present.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository registrations,
            ServerOAuth2AuthorizedClientRepository authorizedClients,
            ReactiveJwtDecoder jwtDecoder,
            ProblemSecurityHandlers problems,
            GatewayProperties properties) {
        BffAuthenticationHandlers handlers = new BffAuthenticationHandlers(properties.webOrigin());
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(cors -> {})
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .securityContextRepository(new WebSessionServerSecurityContextRepository())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .pathMatchers(HttpMethod.OPTIONS)
                        .permitAll()
                        .pathMatchers("/auth/login", "/auth/register", "/auth/callback", "/oauth2/authorization/**")
                        .permitAll()
                        .pathMatchers("/internal/**")
                        .access(internalCallers(properties.internalCallers()))
                        .pathMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .pathMatchers("/auth/session", "/auth/logout", "/api/v1/**")
                        .authenticated()
                        .anyExchange()
                        .denyAll())
                .oauth2Login(login -> login.authenticationMatcher(
                                new PathPatternParserServerWebExchangeMatcher("/auth/callback"))
                        .authorizationRequestResolver(new RegistrationAwareAuthorizationRequestResolver(registrations))
                        .authorizedClientRepository(authorizedClients)
                        .authenticationSuccessHandler(handlers)
                        .authenticationFailureHandler(handlers))
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(
                                        new ReactiveJwtAuthenticationConverterAdapter(jwtConverter())))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .build();
    }

    @Bean
    ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    /** Refreshes the access token of a web session a minute before it expires, for TokenRelay. */
    @Bean
    ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository registrations,
            ServerOAuth2AuthorizedClientRepository authorizedClients) {
        var manager = new DefaultReactiveOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }

    @Bean
    KeycloakOidcUserService oidcUserService(ReactiveJwtDecoder jwtDecoder) {
        return new KeycloakOidcUserService(jwtDecoder);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(properties.webOrigin()));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(
                "Content-Type",
                "Idempotency-Key",
                "If-Match",
                "If-None-Match",
                "Accept-Language",
                "X-CSRF-Token",
                "X-Client-Platform",
                "X-Client-Version",
                "X-Request-Id",
                "Last-Event-ID",
                "traceparent"));
        cors.setExposedHeaders(List.of(
                "ETag",
                "Location",
                "Retry-After",
                "X-Request-Id",
                "X-RateLimit-Remaining",
                "X-RateLimit-Replenish-Rate",
                "X-RateLimit-Burst-Capacity",
                "traceparent"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", cors);
        source.registerCorsConfiguration("/auth/session", cors);
        source.registerCorsConfiguration("/auth/logout", cors);
        return source;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    static JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRoles);
        return converter;
    }

    static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(role -> List.of("STUDENT", "TUTOR", "ADMIN").contains(role))
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    /** Service tokens of the listed clients with scope {@code internal} (R-16). */
    static ReactiveAuthorizationManager<AuthorizationContext> internalCallers(List<String> clients) {
        return (authentication, context) -> authentication
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .<AuthorizationResult>map(token -> {
                    String scope = token.getToken().getClaimAsString("scope");
                    boolean internal =
                            scope != null && List.of(scope.split(" ")).contains("internal");
                    return new AuthorizationDecision(
                            internal && clients.contains(token.getToken().getClaimAsString("azp")));
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
