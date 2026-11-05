package com.toefly.apigateway.application.service;

import com.toefly.apigateway.application.port.in.RevokeSessionsUseCase;
import com.toefly.apigateway.application.port.out.RevocationStorePort;
import com.toefly.apigateway.application.port.out.WebSessionStorePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SessionRevocationService implements RevokeSessionsUseCase {

    /** Longer than the 15-minute access token, so every token issued before the revocation has expired by then. */
    static final Duration KEEP_FOR = Duration.ofMinutes(20);

    private final WebSessionStorePort sessions;
    private final RevocationStorePort revocations;
    private final Clock clock;

    public SessionRevocationService(WebSessionStorePort sessions, RevocationStorePort revocations, Clock clock) {
        this.sessions = sessions;
        this.revocations = revocations;
        this.clock = clock;
    }

    @Override
    public Mono<Void> revoke(String userId) {
        return revocations
                .markRevoked(userId, clock.instant(), KEEP_FOR)
                .then(sessions.deleteAllOf(userId))
                .then();
    }

    @Override
    public Mono<Boolean> isRevoked(String userId, Instant issuedAt) {
        return revocations
                .revokedAt(userId)
                .map(revokedAt -> !issuedAt.isAfter(revokedAt))
                .defaultIfEmpty(false);
    }
}
