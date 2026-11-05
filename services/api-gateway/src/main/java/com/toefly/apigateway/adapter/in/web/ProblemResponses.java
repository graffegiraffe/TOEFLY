package com.toefly.apigateway.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** Errors of the gateway itself as RFC 9457 Problem Details with TOEFLY extensions (docs/05-api-contracts.md §3). */
@Component
public class ProblemResponses {

    static final String BASE_URI = "https://toefly.app/problems/";

    private final JsonMapper json;

    public ProblemResponses(JsonMapper json) {
        this.json = json;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String slug, String title, String detail) {
        return write(exchange, status, slug, title, detail, Map.of());
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String slug,
            String title,
            String detail,
            Map<String, Object> extensions) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", BASE_URI + slug);
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", exchange.getRequest().getPath().value());
        body.put("code", slug.replace('-', '_').toUpperCase(Locale.ROOT));
        String requestId = RequestIdWebFilter.requestId(exchange);
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        String traceparent = exchange.getRequest().getHeaders().getFirst("traceparent");
        if (traceparent != null && traceparent.split("-").length >= 2) {
            body.put("traceId", traceparent.split("-")[1]);
        }
        body.putAll(extensions);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] bytes = json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    public Mono<Void> unauthenticated(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.UNAUTHORIZED, "unauthenticated", "Unauthenticated", "Sign in to continue.");
    }

    public Mono<Void> forbidden(ServerWebExchange exchange, String detail, Map<String, Object> extensions) {
        return write(exchange, HttpStatus.FORBIDDEN, "forbidden", "Forbidden", detail, extensions);
    }
}
