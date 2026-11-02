package com.toefly.common.test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Keycloak-shaped access tokens for MockMvc tests: {@code mvc.perform(get(...).with(JwtFixtures.student(id)))}. */
public final class JwtFixtures {

    public static final String ISSUER = "http://localhost:8180/realms/toefly";

    private JwtFixtures() {}

    public static JwtRequestPostProcessor student(UUID userId) {
        return withRoles(userId, "STUDENT");
    }

    public static JwtRequestPostProcessor tutor(UUID userId) {
        return withRoles(userId, "TUTOR");
    }

    public static JwtRequestPostProcessor admin(UUID userId) {
        return withRoles(userId, "ADMIN");
    }

    public static JwtRequestPostProcessor withRoles(UUID userId, String... roles) {
        return jwt().jwt(token(userId, roles))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
    }

    public static Jwt token(UUID userId, String... roles) {
        Instant issuedAt = Instant.parse("2026-11-01T09:00:00Z");
        return Jwt.withTokenValue("test-token-" + userId)
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(userId.toString())
                .audience(List.of("toefly-web-bff"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }
}
