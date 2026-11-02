package com.toefly.common.security;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BearerTokenPropagationInterceptorTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsCallerToken() {
        Jwt jwt = Jwt.withTokenValue("caller-token")
                .header("alg", "RS256")
                .subject("user")
                .issuedAt(Instant.parse("2026-11-01T09:00:00Z"))
                .expiresAt(Instant.parse("2026-11-01T09:15:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        RestClient.Builder builder = RestClient.builder().requestInterceptor(new BearerTokenPropagationInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://content/api/v1/item-sets"))
                .andExpect(header("Authorization", "Bearer caller-token"))
                .andRespond(withSuccess());

        builder.build().get().uri("http://content/api/v1/item-sets").retrieve().toBodilessEntity();

        server.verify();
    }
}
