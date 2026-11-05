package com.toefly.profile.adapter.in.web;

import com.toefly.profile.application.port.in.SignedInUser;
import com.toefly.profile.domain.consent.ConsentType;
import com.toefly.profile.domain.profile.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Builds the caller from the Keycloak access token. Sign-up choices travel as the claims {@code account_type} and
 * {@code consents.*} (user attributes of the registration form, infra/keycloak/realm/toefly-realm.yaml).
 */
@Component
public class CurrentUser {

    private static final Map<String, ConsentType> SIGN_UP_CONSENTS =
            Map.of("terms", ConsentType.TERMS, "privacy", ConsentType.PRIVACY, "age", ConsentType.AGE_16_PLUS);

    private final HttpServletRequest request;

    public CurrentUser(HttpServletRequest request) {
        this.request = request;
    }

    public SignedInUser get() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("No JWT authentication");
        }
        return fromJwt(token.getToken(), clientIp(request));
    }

    static SignedInUser fromJwt(Jwt jwt, @Nullable String clientIp) {
        return new SignedInUser(
                UUID.fromString(jwt.getSubject()),
                jwt.hasClaim("email") ? jwt.getClaimAsString("email") : "",
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"),
                roles(jwt),
                requestedRole(jwt.getClaimAsString("account_type")),
                signUpConsents(jwt),
                authTime(jwt),
                clientIp);
    }

    private static Set<Role> roles(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> names) {
            for (Object name : names) {
                for (Role role : Role.values()) {
                    if (role.name().equals(String.valueOf(name))) {
                        roles.add(role);
                    }
                }
            }
        }
        return roles;
    }

    private static @Nullable Role requestedRole(@Nullable String accountType) {
        return "TUTOR".equals(accountType) ? Role.TUTOR : null;
    }

    private static Set<ConsentType> signUpConsents(Jwt jwt) {
        Set<ConsentType> given = EnumSet.noneOf(ConsentType.class);
        Map<String, Object> consents = jwt.getClaimAsMap("consents");
        if (consents != null) {
            SIGN_UP_CONSENTS.forEach((claim, type) -> {
                Object value = consents.get(claim);
                if (value != null && !String.valueOf(value).isBlank()) {
                    given.add(type);
                }
            });
        }
        return given;
    }

    private static @Nullable Instant authTime(Jwt jwt) {
        Object value = jwt.getClaims().get("auth_time");
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number seconds) {
            return Instant.ofEpochSecond(seconds.longValue());
        }
        return null;
    }

    /** The gateway appends the client address to {@code X-Forwarded-For}; the first entry is the browser. */
    private static @Nullable String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
