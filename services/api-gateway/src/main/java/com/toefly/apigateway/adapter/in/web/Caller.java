package com.toefly.apigateway.adapter.in.web;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The caller of a request, signed in with the web session cookie or with a Bearer token. */
record Caller(
        String userId,
        Set<String> roles,
        boolean emailVerified,
        @Nullable Instant tokenIssuedAt,
        boolean web) {

    static @Nullable Caller of(@Nullable Principal authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof OidcUser user) {
            return new Caller(
                    user.getSubject(), roles(token), Boolean.TRUE.equals(user.getEmailVerified()), null, true);
        }
        if (authentication instanceof JwtAuthenticationToken token) {
            return new Caller(
                    token.getName(),
                    roles(token),
                    Boolean.TRUE.equals(token.getToken().getClaimAsBoolean("email_verified")),
                    token.getToken().getIssuedAt(),
                    false);
        }
        return null;
    }

    private static Set<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .collect(Collectors.toUnmodifiableSet());
    }
}
