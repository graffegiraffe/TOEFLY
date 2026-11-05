package com.toefly.apigateway.adapter.in.web;

import com.toefly.apigateway.application.port.in.CheckRateLimitUseCase;
import com.toefly.apigateway.domain.EndpointGroup;
import com.toefly.apigateway.domain.RateLimitDecision;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Token buckets per caller and endpoint group; 429 with {@code Retry-After} (docs/05-api-contracts.md §2.10). */
@Component
@Order(40)
public class RateLimitWebFilter implements WebFilter {

    private final CheckRateLimitUseCase rateLimits;
    private final ProblemResponses problems;

    public RateLimitWebFilter(CheckRateLimitUseCase rateLimits, ProblemResponses problems) {
        this.rateLimits = rateLimits;
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/v1/") && !path.startsWith("/auth/")) {
            return chain.filter(exchange);
        }
        EndpointGroup group = EndpointGroup.of(exchange.getRequest().getMethod().name(), path);
        if (path.startsWith("/auth/") && group != EndpointGroup.AUTH) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .mapNotNull(Caller::of)
                .map(caller -> new Subject(caller.userId(), caller.roles()))
                .defaultIfEmpty(new Subject(null, Set.of()))
                .flatMap(subject -> rateLimits.check(subject.userId(), clientAddress(exchange), subject.roles(), group))
                .flatMap(decision -> {
                    headers(exchange.getResponse().getHeaders(), decision);
                    return decision.allowed() ? chain.filter(exchange) : tooManyRequests(exchange, decision);
                });
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, RateLimitDecision decision) {
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        return problems.write(
                exchange,
                HttpStatus.TOO_MANY_REQUESTS,
                "rate-limited",
                "Too many requests",
                "Too many requests. Try again in " + decision.retryAfterSeconds() + " seconds.",
                Map.of());
    }

    private static void headers(HttpHeaders headers, RateLimitDecision decision) {
        if (decision.bucket() == null) {
            return;
        }
        headers.set("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        headers.set(
                "X-RateLimit-Replenish-Rate", Double.toString(decision.bucket().replenishRate()));
        headers.set(
                "X-RateLimit-Burst-Capacity", Integer.toString(decision.bucket().burstCapacity()));
    }

    /**
     * The connection address; behind Cloudflare the trusted {@code CF-Connecting-IP} replaces it in the cluster
     * (docs/10 §8.1), which phase 13 configures together with the trusted proxy ranges.
     */
    private static String clientAddress(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null
                ? "unknown"
                : remote.getAddress().getHostAddress();
    }

    private record Subject(
            @org.jspecify.annotations.Nullable String userId, Set<String> roles) {}
}
