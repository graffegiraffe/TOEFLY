package com.toefly.apigateway.adapter.in.web;

import com.toefly.apigateway.application.port.in.RevokeSessionsUseCase;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** {@code POST /internal/v1/sessions/revocations}, called by profile-service (docs/10-security-privacy.md §4.7). */
@RestController
public class SessionRevocationController {

    private final RevokeSessionsUseCase revocations;

    public SessionRevocationController(RevokeSessionsUseCase revocations) {
        this.revocations = revocations;
    }

    @PostMapping("/internal/v1/sessions/revocations")
    public Mono<ResponseEntity<Void>> revoke(@RequestBody RevocationRequest request) {
        UUID userId = UUID.fromString(request.userId());
        return revocations
                .revoke(userId.toString())
                .thenReturn(ResponseEntity.noContent().build());
    }

    public record RevocationRequest(String userId) {}
}
