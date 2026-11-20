package com.toefly.analytics.application.port.in;

import java.util.Set;
import java.util.UUID;

/** The authenticated user of a request and the roles of the token. */
public record Caller(UUID userId, Set<Role> roles) {

    public Caller {
        roles = Set.copyOf(roles);
    }

    public enum Role {
        STUDENT,
        TUTOR,
        ADMIN
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
