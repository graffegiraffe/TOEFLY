package com.toefly.common.security;

import java.util.Optional;

/** Realm roles of the {@code toefly} Keycloak realm. */
public enum ToeflyRole {
    STUDENT,
    TUTOR,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<ToeflyRole> fromRealmRole(String role) {
        for (ToeflyRole value : values()) {
            if (value.name().equalsIgnoreCase(role)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
