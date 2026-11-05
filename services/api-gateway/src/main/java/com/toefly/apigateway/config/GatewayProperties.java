package com.toefly.apigateway.config;

import com.toefly.apigateway.domain.EndpointGroup;
import com.toefly.apigateway.domain.RateLimitTier;
import com.toefly.apigateway.domain.TokenBucket;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of api-gateway.
 *
 * @param webOrigin the only origin of the web app (CORS, CSRF, redirects after sign-in)
 * @param internalCallers {@code azp} of service clients allowed to call {@code /internal/**} (R-16)
 * @param rateLimits buckets per tier and endpoint group (docs/05-api-contracts.md §2.10)
 */
@ConfigurationProperties("toefly.gateway")
public record GatewayProperties(
        String webOrigin,
        Session session,
        Keycloak keycloak,
        List<String> internalCallers,
        Map<RateLimitTier, Map<EndpointGroup, TokenBucket>> rateLimits) {

    /** {@code __Host-toefly_sid} in cluster environments, {@code toefly_sid} locally (ADR-0023). */
    public record Session(String cookieName, boolean secureCookie, int maxInactiveDays) {}

    public record Keycloak(String endSessionUri) {}
}
