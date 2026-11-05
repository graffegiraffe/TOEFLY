package com.toefly.apigateway.adapter.in.web;

import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Keeps or creates {@code X-Request-Id} for the downstream request and the response (docs/05 §2.11). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdWebFilter implements WebFilter {

    static final String HEADER = "X-Request-Id";
    private static final String ATTRIBUTE = RequestIdWebFilter.class.getName();
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = incoming != null && SAFE.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        exchange.getAttributes().put(ATTRIBUTE, requestId);
        exchange.getResponse().getHeaders().set(HEADER, requestId);
        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER, requestId)))
                .build();
        return chain.filter(mutated);
    }

    static @Nullable String requestId(ServerWebExchange exchange) {
        return exchange.getAttribute(ATTRIBUTE);
    }
}
