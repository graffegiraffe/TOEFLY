package com.toefly.apigateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers(disabledWithoutDocker = true)
class GatewayComponentIT {

    static final String WEB = "http://app.toefly.localhost:5173";

    @Container
    static final GenericContainer<?> VALKEY = new GenericContainer<>("valkey/valkey:8.1").withExposedPorts(6379);

    static final AtomicReference<HttpHeaders> DOWNSTREAM = new AtomicReference<>();
    static final DisposableServer PROFILE = HttpServer.create()
            .port(0)
            .route(routes -> routes.route(request -> true, (request, response) -> {
                HttpHeaders headers = new HttpHeaders();
                request.requestHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));
                DOWNSTREAM.set(headers);
                return response.header("Content-Type", "application/json").sendString(Mono.just("{\"id\":\"stub\"}"));
            }))
            .bindNow();

    static final Map<String, Jwt> TOKENS = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
        registry.add("toefly.gateway.services.profile-service", () -> "http://localhost:" + PROFILE.port());
        // The cluster value (docs/10 §4.2); local runs use Lax.
        registry.add("toefly.gateway.session.same-site", () -> "Strict");
        registry.add("toefly.gateway.rate-limits.STUDENT_PRO.GENERAL.replenish-rate", () -> "0.5");
        registry.add("toefly.gateway.rate-limits.STUDENT_PRO.GENERAL.burst-capacity", () -> "3");
    }

    @AfterAll
    static void stop() {
        PROFILE.disposeNow();
    }

    @Autowired
    WebTestClient client;

    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void decoder() {
        when(jwtDecoder.decode(anyString())).thenAnswer(call -> {
            Jwt jwt = TOKENS.get(call.<String>getArgument(0));
            return jwt == null ? Mono.error(new BadJwtException("unknown token")) : Mono.just(jwt);
        });
    }

    static String token(String userId, List<String> roles, boolean emailVerified, Instant issuedAt) {
        String value = "token-" + UUID.randomUUID();
        TOKENS.put(
                value,
                Jwt.withTokenValue(value)
                        .header("alg", "RS256")
                        .subject(userId)
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusSeconds(900))
                        .claim("azp", "toefly-ios")
                        .claim("email_verified", emailVerified)
                        .claim("realm_access", Map.of("roles", roles))
                        .build());
        return value;
    }

    static String student() {
        return token(UUID.randomUUID().toString(), List.of("STUDENT"), true, Instant.now());
    }

    static String serviceToken(String clientId) {
        String value = "svc-" + UUID.randomUUID();
        TOKENS.put(
                value,
                Jwt.withTokenValue(value)
                        .header("alg", "RS256")
                        .subject(UUID.randomUUID().toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .claim("azp", clientId)
                        .claim("scope", "internal")
                        .build());
        return value;
    }

    @Test
    @DisplayName("12 phase 1 DoD: a request without a token gets 401 Problem Details")
    void unauthenticatedIsProblem() {
        client.get()
                .uri("/api/v1/me")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://toefly.app/problems/unauthenticated")
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.status")
                .isEqualTo(401)
                .jsonPath("$.requestId")
                .exists();
    }

    @Test
    @DisplayName("12 phase 1 DoD: a STUDENT on an admin route gets 403 Problem Details")
    void studentOnAdminRouteIsForbidden() {
        client.get()
                .uri("/api/v1/admin/users")
                .headers(h -> h.setBearerAuth(student()))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("NFR-SEC-05 exceeding the bucket gives 429 with Retry-After")
    void rateLimited() {
        String token = student();
        for (int i = 0; i < 3; i++) {
            client.get()
                    .uri("/api/v1/me")
                    .headers(h -> h.setBearerAuth(token))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .exists("X-RateLimit-Remaining");
        }
        client.get()
                .uri("/api/v1/me")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isEqualTo(429)
                .expectHeader()
                .valueEquals(HttpHeaders.RETRY_AFTER, "2")
                .expectHeader()
                .valueEquals("X-RateLimit-Burst-Capacity", "3")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("Bearer requests reach the service with the token and a request id, never with cookies")
    void routesToProfileService() {
        String token = student();

        client.get()
                .uri("/api/v1/me")
                .headers(h -> h.setBearerAuth(token))
                .cookie("toefly_sid", "abc")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader()
                .valueEquals("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo("stub");

        HttpHeaders seen = DOWNSTREAM.get();
        assertThat(seen.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
        assertThat(seen.getFirst("X-Request-Id")).isNotBlank();
        assertThat(seen.containsHeader(HttpHeaders.COOKIE)).isFalse();
    }

    @Test
    @DisplayName("AC-01.02.3 without a verified email only the profile is readable")
    void emailVerifiedGate() {
        String token = token(UUID.randomUUID().toString(), List.of("STUDENT"), false, Instant.now());

        client.get()
                .uri("/api/v1/me")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isOk();
        client.put()
                .uri("/api/v1/me/preferences")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    @DisplayName("AC-01.05.4 after revocation earlier tokens of the user get 401")
    void revocation() {
        String userId = UUID.randomUUID().toString();
        String earlier = token(userId, List.of("STUDENT"), true, Instant.now().minusSeconds(30));

        client.post()
                .uri("/internal/v1/sessions/revocations")
                .headers(h -> h.setBearerAuth(student()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userId", userId))
                .exchange()
                .expectStatus()
                .isForbidden();
        client.post()
                .uri("/internal/v1/sessions/revocations")
                .headers(h -> h.setBearerAuth(serviceToken("svc-profile-service")))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userId", userId))
                .exchange()
                .expectStatus()
                .isNoContent();

        client.get()
                .uri("/api/v1/me")
                .headers(h -> h.setBearerAuth(earlier))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        String later = token(userId, List.of("STUDENT"), true, Instant.now().plusSeconds(2));
        client.get()
                .uri("/api/v1/me")
                .headers(h -> h.setBearerAuth(later))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void sessionWithoutCookieIsUnauthenticated() {
        client.get()
                .uri("/auth/session")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("AC-01.05.1 sign-in starts Authorization Code with PKCE and a strict HttpOnly session cookie")
    void loginStartsPkceFlow() {
        var login = client.get()
                .uri("/auth/login?returnTo=/onboarding/goal")
                .exchange()
                .expectStatus()
                .isFound()
                .expectHeader()
                .location("/oauth2/authorization/keycloak")
                .returnResult(Void.class);
        ResponseCookie cookie = login.getResponseCookies().getFirst("toefly_sid");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");

        String location = client.get()
                .uri("/oauth2/authorization/keycloak")
                .cookie("toefly_sid", cookie.getValue())
                .exchange()
                .expectStatus()
                .isFound()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getLocation()
                .toString();
        assertThat(location)
                .startsWith("http://localhost:8180/realms/toefly/protocol/openid-connect/auth?")
                .contains("code_challenge_method=S256")
                .contains("client_id=toefly-web-bff")
                .contains("ui_locales=en")
                .contains("redirect_uri=http://localhost:");
    }

    @Test
    @DisplayName("AC-01.01.1 sign-up opens the Keycloak registration page")
    void registerOpensRegistrationPage() {
        String location = client.get()
                .uri("/oauth2/authorization/keycloak?register")
                .exchange()
                .expectStatus()
                .isFound()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getLocation()
                .toString();

        assertThat(location).contains("/protocol/openid-connect/registrations?").contains("code_challenge=");
    }

    @Test
    @DisplayName("AC-31.04.1 re-authentication asks Keycloak for the credentials again")
    void reauthenticationPromptsLogin() {
        client.get()
                .uri("/auth/login?returnTo=/settings/privacy&reauth=true")
                .exchange()
                .expectStatus()
                .isFound()
                .expectHeader()
                .location("/oauth2/authorization/keycloak?reauth");

        String location = client.get()
                .uri("/oauth2/authorization/keycloak?reauth")
                .exchange()
                .expectStatus()
                .isFound()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getLocation()
                .toString();

        assertThat(location).contains("/protocol/openid-connect/auth?").contains("prompt=login");
    }

    @Test
    @DisplayName("docs/10 §8.5 CORS allows only the web origin with credentials")
    void cors() {
        client.options()
                .uri("/api/v1/me")
                .header(HttpHeaders.ORIGIN, WEB)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-csrf-token,content-type")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB)
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        client.options()
                .uri("/api/v1/me")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus()
                .isForbidden();
    }
}
