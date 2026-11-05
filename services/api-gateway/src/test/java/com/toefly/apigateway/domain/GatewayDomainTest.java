package com.toefly.apigateway.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GatewayDomainTest {

    @ParameterizedTest
    @CsvSource({
        "GET, /api/v1/me, GENERAL",
        "POST, /api/v1/chat-threads/1/messages, AI",
        "POST, /api/v1/submissions/7/submission, AI",
        "GET, /api/v1/submissions/7, GENERAL",
        "POST, /api/v1/recordings, AI",
        "POST, /api/v1/materials, UPLOADS",
        "GET, /auth/login, AUTH",
        "GET, /auth/callback, AUTH"
    })
    @DisplayName("NFR-SEC-05 endpoints fall into their rate limit groups")
    void endpointGroups(String method, String path, EndpointGroup group) {
        assertThat(EndpointGroup.of(method, path)).isEqualTo(group);
    }

    @Test
    void onlySignInClosesWhenValkeyIsDown() {
        assertThat(EndpointGroup.AUTH.failsClosed()).isTrue();
        assertThat(EndpointGroup.GENERAL.failsClosed()).isFalse();
    }

    @Test
    @DisplayName("NFR-SEC-05 Retry-After is the time until the request cost is back")
    void retryAfter() {
        TokenBucket tenPerMinute = new TokenBucket(1, 60, 6);

        assertThat(tenPerMinute.secondsUntilAllowed(0)).isEqualTo(6);
        assertThat(tenPerMinute.secondsUntilAllowed(5.5)).isEqualTo(1);
        assertThat(tenPerMinute.secondsUntilAllowed(6)).isZero();
        assertThat(RateLimitDecision.of(tenPerMinute, false, 2).retryAfterSeconds())
                .isEqualTo(4);
        assertThat(RateLimitDecision.of(tenPerMinute, true, 30.7).remaining()).isEqualTo(30);
        assertThat(RateLimitDecision.unlimited().bucket()).isNull();
        assertThat(RateLimitDecision.degraded(tenPerMinute).allowed()).isTrue();
    }

    @Test
    void rejectsBucketsThatNeverAllowARequest() {
        assertThatThrownBy(() -> new TokenBucket(1, 5, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(0, 5, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example", "//evil.example", "/\\evil", "/auth/login", "javascript:alert(1)"})
    @DisplayName("ADR-0023 returnTo accepts only relative app paths")
    void unsafeReturnPathsFallBackToHome(String value) {
        assertThat(ReturnPath.parse(value)).isEqualTo(ReturnPath.HOME);
    }

    @Test
    void keepsAppPaths() {
        assertThat(ReturnPath.parse("/onboarding/goal").value()).isEqualTo("/onboarding/goal");
        assertThat(ReturnPath.parse(null)).isEqualTo(ReturnPath.HOME);
        assertThatThrownBy(() -> new ReturnPath("//x")).isInstanceOf(IllegalArgumentException.class);
    }
}
