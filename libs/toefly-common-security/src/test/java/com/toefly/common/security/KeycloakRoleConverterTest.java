package com.toefly.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    @Test
    void mapsKnownRealmRolesAndUsesSubjectAsName() {
        Jwt jwt = jwt(Map.of("roles", List.of("student", "TUTOR", "offline_access", "uma_authorization")));

        var authentication = converter.convert(jwt);

        assertThat(authentication.getName()).isEqualTo("7b1c9a44-0f3e-4f0b-9a60-6a53f1d2c871");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT", "ROLE_TUTOR");
    }

    @Test
    void tokenWithoutRealmRolesHasNoAuthorities() {
        assertThat(converter.convert(jwt(Map.of())).getAuthorities()).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> realmAccess) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("7b1c9a44-0f3e-4f0b-9a60-6a53f1d2c871")
                .issuedAt(Instant.parse("2026-11-01T09:00:00Z"))
                .expiresAt(Instant.parse("2026-11-01T09:15:00Z"))
                .claim("realm_access", realmAccess)
                .build();
    }
}
