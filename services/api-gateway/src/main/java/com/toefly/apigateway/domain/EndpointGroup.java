package com.toefly.apigateway.domain;

import java.util.List;

/** Groups of endpoints with their own buckets (docs/05-api-contracts.md §2.10, docs/10 §8.1). */
public enum EndpointGroup {
    GENERAL,
    AI,
    UPLOADS,
    /** Sign-in endpoints of the BFF, limited per client address and closed when Valkey is down. */
    AUTH;

    private static final List<String> AI_PATHS = List.of(
            "/api/v1/chat-threads/", "/api/v1/recordings", "/api/v1/word-suggestions", "/api/v1/interview-sessions");

    public static EndpointGroup of(String method, String path) {
        if (path.startsWith("/auth/login") || path.startsWith("/auth/register") || path.startsWith("/auth/callback")) {
            return AUTH;
        }
        if ("POST".equals(method) && path.startsWith("/api/v1/materials")) {
            return UPLOADS;
        }
        if ("POST".equals(method)
                && (AI_PATHS.stream().anyMatch(path::startsWith)
                        || (path.startsWith("/api/v1/submissions/") && path.endsWith("/submission")))) {
            return AI;
        }
        return GENERAL;
    }

    public boolean failsClosed() {
        return this == AUTH;
    }
}
