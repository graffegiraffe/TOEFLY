package com.toefly.aigateway.adapter.in.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Name of the calling service ({@code azp} {@code svc-planning-service} → {@code planning-service}) or user. */
final class Callers {

    private Callers() {}

    static String service() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            Object azp = jwt.getToken().getClaims().get("azp");
            if (azp != null) {
                String client = azp.toString();
                return client.startsWith("svc-") ? client.substring(4) : client;
            }
        }
        return authentication == null ? "unknown" : authentication.getName();
    }

    static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "unknown" : authentication.getName();
    }
}
