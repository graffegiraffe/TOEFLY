package com.toefly.apigateway.application.port.in;

import java.time.Instant;
import reactor.core.publisher.Mono;

public interface RevokeSessionsUseCase {

    /** Ends every web session of the user and rejects tokens issued before now (docs/10 §4.7). */
    Mono<Void> revoke(String userId);

    /** Whether a token issued at {@code issuedAt} belongs to a revoked sign-in. */
    Mono<Boolean> isRevoked(String userId, Instant issuedAt);
}
