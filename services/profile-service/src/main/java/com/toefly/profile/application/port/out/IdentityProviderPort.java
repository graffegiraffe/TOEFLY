package com.toefly.profile.application.port.out;

import java.util.UUID;

/** Changes of the Keycloak account through the Admin API (client {@code svc-profile-service}, R-18). */
public interface IdentityProviderPort {

    /** Gives the TUTOR role instead of the default STUDENT role. */
    void makeTutor(UUID userId);

    /** Disables the account and ends its Keycloak sessions. */
    void disableUser(UUID userId);
}
