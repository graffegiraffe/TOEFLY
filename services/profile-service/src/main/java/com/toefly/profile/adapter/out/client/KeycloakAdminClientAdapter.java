package com.toefly.profile.adapter.out.client;

import com.toefly.profile.application.port.out.IdentityProviderPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Keycloak Admin REST API of realm {@code toefly} called with the token of {@code svc-profile-service}. */
@Component
public class KeycloakAdminClientAdapter implements IdentityProviderPort {

    private static final ParameterizedTypeReference<Map<String, Object>> ROLE = new ParameterizedTypeReference<>() {};

    /** Clients that receive offline tokens (scope offline_access, docs/10 §4.1). */
    private static final List<String> OFFLINE_CLIENTS = List.of("toefly-web-bff", "toefly-ios");

    private final RestClient keycloak;

    public KeycloakAdminClientAdapter(@Qualifier("keycloakAdminRestClient") RestClient keycloak) {
        this.keycloak = keycloak;
    }

    @Override
    public void makeTutor(UUID userId) {
        Map<String, Object> tutor = role("TUTOR");
        Map<String, Object> student = role("STUDENT");
        keycloak.post()
                .uri("/users/{id}/role-mappings/realm", userId)
                .body(List.of(tutor))
                .retrieve()
                .toBodilessEntity();
        keycloak.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/users/{id}/role-mappings/realm", userId)
                .body(List.of(student))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void addTutorRole(UUID userId) {
        keycloak.post()
                .uri("/users/{id}/role-mappings/realm", userId)
                .body(List.of(role("TUTOR")))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void endAllSessions(UUID userId) {
        keycloak.post().uri("/users/{id}/logout", userId).retrieve().toBodilessEntity();
        for (String client : OFFLINE_CLIENTS) {
            // Revoking the consent of a client revokes its offline tokens; a client never used has none (404).
            keycloak.delete()
                    .uri("/users/{id}/consents/{client}", userId, client)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {})
                    .toBodilessEntity();
        }
    }

    @Override
    public void disableUser(UUID userId) {
        keycloak.put()
                .uri("/users/{id}", userId)
                .body(Map.of("enabled", false))
                .retrieve()
                .toBodilessEntity();
        keycloak.post().uri("/users/{id}/logout", userId).retrieve().toBodilessEntity();
    }

    private Map<String, Object> role(String name) {
        return keycloak.get().uri("/roles/{name}", name).retrieve().body(ROLE);
    }
}
