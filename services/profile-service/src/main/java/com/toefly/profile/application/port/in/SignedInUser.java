package com.toefly.profile.application.port.in;

import com.toefly.profile.domain.consent.ConsentType;
import com.toefly.profile.domain.profile.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The caller as the access token describes it. {@code requestedRole} and {@code signUpConsents} come from the
 * attributes of the Keycloak sign-up form and matter only on the first sign-in.
 */
public record SignedInUser(
        UUID userId,
        String email,
        boolean emailVerified,
        @Nullable String displayName,
        Set<Role> roles,
        @Nullable Role requestedRole,
        Set<ConsentType> signUpConsents,
        @Nullable Instant authTime,
        @Nullable String clientIp) {

    public SignedInUser {
        roles = Set.copyOf(roles);
        signUpConsents = Set.copyOf(signUpConsents);
    }
}
