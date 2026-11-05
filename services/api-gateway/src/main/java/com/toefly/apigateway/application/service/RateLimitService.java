package com.toefly.apigateway.application.service;

import com.toefly.apigateway.application.port.in.CheckRateLimitUseCase;
import com.toefly.apigateway.application.port.out.EntitlementCachePort;
import com.toefly.apigateway.application.port.out.TokenBucketPort;
import com.toefly.apigateway.domain.EndpointGroup;
import com.toefly.apigateway.domain.RateLimitDecision;
import com.toefly.apigateway.domain.RateLimitTier;
import com.toefly.apigateway.domain.TokenBucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RateLimitService implements CheckRateLimitUseCase {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final TokenBucketPort buckets;
    private final EntitlementCachePort entitlements;
    private final RateLimitPolicy policy;
    private final Counter degraded;

    public RateLimitService(
            TokenBucketPort buckets, EntitlementCachePort entitlements, RateLimitPolicy policy, MeterRegistry meters) {
        this.buckets = buckets;
        this.entitlements = entitlements;
        this.policy = policy;
        this.degraded = Counter.builder("toefly.gateway.ratelimit.degraded")
                .description("Requests let through because Valkey was unavailable")
                .register(meters);
    }

    @Override
    public Mono<RateLimitDecision> check(
            @Nullable String userId, String clientAddress, Set<String> roles, EndpointGroup group) {
        Mono<RateLimitTier> tier = group == EndpointGroup.AUTH || userId == null
                ? Mono.just(RateLimitTier.ANONYMOUS)
                : tier(userId, roles);
        String subject = group == EndpointGroup.AUTH || userId == null ? "ip:" + clientAddress : "user:" + userId;
        return tier.flatMap(t -> {
            TokenBucket bucket = policy.bucket(t, group);
            if (bucket == null) {
                return Mono.just(RateLimitDecision.unlimited());
            }
            return buckets.take("rl:" + group.name().toLowerCase() + ":" + subject, bucket)
                    .onErrorResume(e -> {
                        if (group.failsClosed()) {
                            log.warn("Rate limiter unavailable, closing {}", group, e);
                            return Mono.just(RateLimitDecision.of(bucket, false, 0));
                        }
                        degraded.increment();
                        return Mono.just(RateLimitDecision.degraded(bucket));
                    });
        });
    }

    private Mono<RateLimitTier> tier(String userId, Set<String> roles) {
        if (roles.contains("ADMIN")) {
            return Mono.just(RateLimitTier.ADMIN);
        }
        if (roles.contains("TUTOR")) {
            return Mono.just(RateLimitTier.TUTOR);
        }
        return entitlements.learnerTier(userId).onErrorReturn(RateLimitTier.STUDENT_FREE);
    }

    /** Buckets per tier and endpoint group; a missing entry means no limit. */
    public record RateLimitPolicy(Map<RateLimitTier, Map<EndpointGroup, TokenBucket>> buckets) {

        public @Nullable TokenBucket bucket(RateLimitTier tier, EndpointGroup group) {
            RateLimitTier owner = group == EndpointGroup.AUTH ? RateLimitTier.ANONYMOUS : tier;
            return buckets.getOrDefault(owner, Map.of()).get(group);
        }
    }
}
