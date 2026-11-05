package com.toefly.apigateway.adapter.out.valkey;

import com.toefly.apigateway.application.port.out.TokenBucketPort;
import com.toefly.apigateway.domain.RateLimitDecision;
import com.toefly.apigateway.domain.TokenBucket;
import java.time.Clock;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ValkeyTokenBucketAdapter implements TokenBucketPort {

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/token-bucket.lua"), List.class);

    private final ReactiveStringRedisTemplate valkey;
    private final Clock clock;

    public ValkeyTokenBucketAdapter(ReactiveStringRedisTemplate valkey, Clock clock) {
        this.valkey = valkey;
        this.clock = clock;
    }

    @Override
    public Mono<RateLimitDecision> take(String key, TokenBucket bucket) {
        double now = clock.millis() / 1000.0;
        return valkey.execute(
                        SCRIPT,
                        List.of(key + ":tokens", key + ":ts"),
                        List.of(
                                Double.toString(bucket.replenishRate()),
                                Integer.toString(bucket.burstCapacity()),
                                Double.toString(now),
                                Integer.toString(bucket.requestedTokens())))
                .next()
                .map(result -> RateLimitDecision.of(
                        bucket,
                        Long.valueOf(1).equals(result.get(0)),
                        Double.parseDouble(String.valueOf(result.get(1)))));
    }
}
