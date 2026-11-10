package com.toefly.common.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns {@code realm_access.roles} of a Keycloak access token into {@code ROLE_STUDENT}, {@code ROLE_TUTOR} and
 * {@code ROLE_ADMIN}; other realm roles are ignored. A client-credentials token with the scope {@code internal}
 * also gets {@code ROLE_SERVICE}, which {@code /internal/**} requires (docs/10-security-privacy.md §7.2). The
 * principal name is the {@code sub} claim (the user id).
 */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    static final String SERVICE_AUTHORITY = "ROLE_SERVICE";
    static final String INTERNAL_SCOPE = "internal";

    static List<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            roles.stream()
                    .map(String::valueOf)
                    .map(ToeflyRole::fromRealmRole)
                    .flatMap(Optional::stream)
                    .distinct()
                    .forEach(role -> authorities.add(new SimpleGrantedAuthority(role.authority())));
        }
        if (scopes(jwt).contains(INTERNAL_SCOPE)) {
            authorities.add(new SimpleGrantedAuthority(SERVICE_AUTHORITY));
        }
        return List.copyOf(authorities);
    }

    /** The {@code scope} claim is a space-separated string in Keycloak tokens; a list is accepted too. */
    private static List<String> scopes(Jwt jwt) {
        Object scope = jwt.getClaims().get("scope");
        if (scope instanceof String text) {
            return Arrays.asList(text.trim().split("\\s+"));
        }
        if (scope instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
