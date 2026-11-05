package com.toefly.apigateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.apigateway.application.port.out.RevocationStorePort;
import com.toefly.apigateway.application.port.out.TokenBucketPort;
import com.toefly.apigateway.application.port.out.WebSessionStorePort;
import com.toefly.apigateway.application.service.RateLimitService.RateLimitPolicy;
import com.toefly.apigateway.domain.EndpointGroup;
import com.toefly.apigateway.domain.RateLimitDecision;
import com.toefly.apigateway.domain.RateLimitTier;
import com.toefly.apigateway.domain.TokenBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GatewayServicesTest {

    static final TokenBucket GENERAL = new TokenBucket(10, 40, 1);
    static final TokenBucket ADMIN = new TokenBucket(30, 100, 1);
    static final TokenBucket AUTH = new TokenBucket(1, 60, 6);

    final List<String> keys = new ArrayList<>();
    boolean valkeyDown;

    final TokenBucketPort buckets = (key, bucket) -> {
        if (valkeyDown) {
            return Mono.error(new IllegalStateException("down"));
        }
        keys.add(key + "@" + bucket.burstCapacity());
        return Mono.just(RateLimitDecision.of(bucket, true, bucket.burstCapacity() - 1));
    };

    final RateLimitService rateLimits = new RateLimitService(
            buckets,
            userId -> Mono.just(RateLimitTier.STUDENT_PRO),
            new RateLimitPolicy(Map.of(
                    RateLimitTier.ANONYMOUS, Map.of(EndpointGroup.AUTH, AUTH),
                    RateLimitTier.STUDENT_PRO, Map.of(EndpointGroup.GENERAL, GENERAL),
                    RateLimitTier.ADMIN, Map.of(EndpointGroup.GENERAL, ADMIN))),
            new SimpleMeterRegistry());

    @Test
    @DisplayName("NFR-SEC-05 signed-in callers are limited by user and plan, anonymous ones by address")
    void picksSubjectAndTier() {
        rateLimits
                .check("u1", "10.0.0.1", Set.of("STUDENT"), EndpointGroup.GENERAL)
                .block();
        rateLimits
                .check("u2", "10.0.0.1", Set.of("ADMIN"), EndpointGroup.GENERAL)
                .block();
        rateLimits.check(null, "10.0.0.9", Set.of(), EndpointGroup.AUTH).block();

        assertThat(keys).containsExactly("rl:general:user:u1@40", "rl:general:user:u2@100", "rl:auth:ip:10.0.0.9@60");
    }

    @Test
    void groupWithoutBucketIsUnlimited() {
        RateLimitDecision decision = rateLimits
                .check("u1", "10.0.0.1", Set.of(), EndpointGroup.UPLOADS)
                .block();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.bucket()).isNull();
    }

    @Test
    @DisplayName("docs/10 §8.1 Valkey outage opens general limits and closes sign-in limits")
    void degradation() {
        valkeyDown = true;

        assertThat(rateLimits
                        .check("u1", "ip", Set.of(), EndpointGroup.GENERAL)
                        .block()
                        .allowed())
                .isTrue();
        assertThat(rateLimits
                        .check(null, "ip", Set.of(), EndpointGroup.AUTH)
                        .block()
                        .allowed())
                .isFalse();
    }

    @Test
    @DisplayName("AC-01.05.4 revocation ends sessions and rejects tokens issued before it")
    void revocation() {
        Map<String, Instant> revoked = new HashMap<>();
        List<String> deleted = new ArrayList<>();
        Clock clock = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);
        RevocationStorePort store = new RevocationStorePort() {
            @Override
            public Mono<Void> markRevoked(String userId, Instant revokedAt, Duration keepFor) {
                revoked.put(userId, revokedAt);
                return Mono.empty();
            }

            @Override
            public Mono<Instant> revokedAt(String userId) {
                return Mono.justOrEmpty(revoked.get(userId));
            }
        };
        WebSessionStorePort sessions = userId -> {
            deleted.add(userId);
            return Mono.just(2L);
        };
        var service = new SessionRevocationService(sessions, store, clock);

        service.revoke("u1").block();

        assertThat(deleted).containsExactly("u1");
        assertThat(service.isRevoked("u1", clock.instant().minusSeconds(60)).block())
                .isTrue();
        assertThat(service.isRevoked("u1", clock.instant().plusSeconds(1)).block())
                .isFalse();
        assertThat(service.isRevoked("u2", clock.instant()).block()).isFalse();
    }
}
