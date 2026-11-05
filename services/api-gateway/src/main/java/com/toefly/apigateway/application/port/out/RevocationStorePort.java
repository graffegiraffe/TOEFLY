package com.toefly.apigateway.application.port.out;

import java.time.Duration;
import java.time.Instant;
import reactor.core.publisher.Mono;

public interface RevocationStorePort {

    Mono<Void> markRevoked(String userId, Instant revokedAt, Duration keepFor);

    Mono<Instant> revokedAt(String userId);
}
