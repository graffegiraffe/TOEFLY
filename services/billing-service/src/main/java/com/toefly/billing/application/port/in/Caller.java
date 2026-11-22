package com.toefly.billing.application.port.in;

import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The caller of a request: {@code sub} of the token, its roles and email. */
public record Caller(UUID userId, Set<Role> roles, @Nullable String email) {

    public Caller {
        roles = Set.copyOf(roles);
    }

    public boolean is(Role role) {
        return roles.contains(role);
    }

    public enum Role {
        STUDENT,
        TUTOR,
        ADMIN,
        SERVICE
    }
}
