package com.toefly.profile.application.port.in;

public interface RevokeSessionsUseCase {

    /**
     * Ends every Keycloak session and offline token of the user and every web session in api-gateway, so the next
     * request from any device gets 401 (AC-01.05.4).
     */
    void signOutEverywhere(SignedInUser user);
}
