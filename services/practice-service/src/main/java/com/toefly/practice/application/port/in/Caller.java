package com.toefly.practice.application.port.in;

import java.util.Set;
import java.util.UUID;

/** Who calls: the user of the token and the roles it carries. */
public record Caller(UUID userId, Set<Role> roles) {

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
