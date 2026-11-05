package com.toefly.profile.application.port.out;

import java.util.UUID;

/** Changes of the Keycloak account through the Admin API (client {@code svc-profile-service}, R-18). */
public interface IdentityProviderPort {

    /** Gives the TUTOR role instead of the default STUDENT role. */
    void makeTutor(UUID userId);

    /** Adds the TUTOR role and keeps the other roles (POST /me/tutor-role). */
    void addTutorRole(UUID userId);

    /** Ends every Keycloak session of the user and revokes the offline tokens of the web and iOS clients. */
    void endAllSessions(UUID userId);

    /** Disables the account and ends its Keycloak sessions. */
    void disableUser(UUID userId);
}
