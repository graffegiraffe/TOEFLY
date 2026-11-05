package com.toefly.profile.adapter.out.client;

import com.toefly.profile.application.port.out.SessionRevocationPort;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** {@code POST /internal/v1/sessions/revocations} of api-gateway (docs/10-security-privacy.md §4.7). */
@Component
public class GatewaySessionRevocationAdapter implements SessionRevocationPort {

    private final RestClient gateway;

    public GatewaySessionRevocationAdapter(@Qualifier("gatewayRestClient") RestClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public void revokeSessions(UUID userId) {
        gateway.post()
                .uri("/internal/v1/sessions/revocations")
                .body(Map.of("userId", userId.toString()))
                .retrieve()
                .toBodilessEntity();
    }
}
