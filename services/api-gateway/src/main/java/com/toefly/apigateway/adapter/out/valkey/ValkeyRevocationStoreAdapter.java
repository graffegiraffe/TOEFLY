package com.toefly.apigateway.adapter.out.valkey;

import com.toefly.apigateway.application.port.out.RevocationStorePort;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** {@code revoked:{userId} = <epoch seconds>} (docs/10-security-privacy.md §4.7). */
@Component
public class ValkeyRevocationStoreAdapter implements RevocationStorePort {

    private final ReactiveStringRedisTemplate valkey;

    public ValkeyRevocationStoreAdapter(ReactiveStringRedisTemplate valkey) {
        this.valkey = valkey;
    }

    @Override
    public Mono<Void> markRevoked(String userId, Instant revokedAt, Duration keepFor) {
        return valkey.opsForValue()
                .set(key(userId), Long.toString(revokedAt.getEpochSecond()), keepFor)
                .then();
    }

    @Override
    public Mono<Instant> revokedAt(String userId) {
        return valkey.opsForValue().get(key(userId)).map(value -> Instant.ofEpochSecond(Long.parseLong(value)));
    }

    private static String key(String userId) {
        return "revoked:" + userId;
    }
}
