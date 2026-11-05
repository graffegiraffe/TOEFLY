package com.toefly.profile.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toefly.common.test.ApicurioTestcontainersConfiguration;
import com.toefly.common.test.KafkaTestcontainersConfiguration;
import com.toefly.common.test.OutboxAssertions;
import com.toefly.common.test.PostgresTestcontainersConfiguration;
import com.toefly.profile.application.port.out.IdentityProviderPort;
import com.toefly.profile.application.port.out.SessionRevocationPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import({
    PostgresTestcontainersConfiguration.class,
    KafkaTestcontainersConfiguration.class,
    ApicurioTestcontainersConfiguration.class
})
class ProfileComponentIT {

    static final String PROFILE_CREATED = "toefly.profile.profile-created.v1";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    IdentityProviderPort identity;

    @MockitoBean
    SessionRevocationPort sessions;

    static JwtRequestPostProcessor student(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString())
                        .claim("email", userId + "@example.com")
                        .claim("email_verified", true)
                        .claim("realm_access", Map.of("roles", List.of("STUDENT")))
                        .claim("consents", Map.of("terms", "accepted", "privacy", "accepted", "age", "accepted"))
                        .claim("auth_time", Instant.now().getEpochSecond()))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    long events(String topic, UUID userId) {
        return OutboxAssertions.published(jdbc, topic).stream()
                .filter(event -> event.partitionKey().equals(userId.toString()))
                .count();
    }

    @Test
    @DisplayName("AC-01.01.5 repeated and concurrent first requests publish profile-created exactly once")
    void profileCreatedOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        Callable<Integer> call = () -> mvc.perform(get("/api/v1/me").with(student(userId)))
                .andReturn()
                .getResponse()
                .getStatus();

        try (var pool = Executors.newFixedThreadPool(6)) {
            List<Integer> statuses = pool.invokeAll(List.of(call, call, call, call, call, call)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
            assertThat(statuses).containsOnly(200);
        }
        mvc.perform(get("/api/v1/me").with(student(userId)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.onboardingStatus").value("NOT_STARTED"));

        assertThat(events(PROFILE_CREATED, userId)).isEqualTo(1);
        assertThat(events("toefly.profile.consent-changed.v1", userId)).isEqualTo(3);
        var event = OutboxAssertions.published(jdbc, PROFILE_CREATED).stream()
                .filter(e -> e.partitionKey().equals(userId.toString()))
                .findFirst()
                .orElseThrow();
        assertThat(event.source()).isEqualTo("/toefly/profile-service");
        assertThat(event.subject()).isEqualTo("users/" + userId);
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-02.04.1 the onboarding completes once and publishes onboarding-completed")
    void onboardingFlow() throws Exception {
        UUID userId = UUID.randomUUID();
        String examDate = LocalDate.now().plusDays(45).toString();

        mvc.perform(put("/api/v1/me/onboarding")
                        .with(student(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overallTarget\":5.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.nextStep").value("EXAM_DATE"));

        mvc.perform(put("/api/v1/me/onboarding")
                        .with(student(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"examBooked\":true,\"examDate\":\"" + examDate + "\",\"minutesPerDay\":45,"
                                + "\"preferredStudyTime\":\"08:30\",\"timezone\":\"Europe/Minsk\","
                                + "\"explanationLanguage\":\"ru\",\"complete\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.examDate").value(examDate))
                .andExpect(jsonPath("$.explanationLanguage").value("ru"));

        assertThat(events("toefly.profile.onboarding-completed.v1", userId)).isEqualTo(1);

        mvc.perform(put("/api/v1/me/onboarding")
                        .with(student(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"complete\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://toefly.app/problems/conflict"));

        mvc.perform(get("/api/v1/me/study-goal").with(student(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.minutesPerDay").value(45));
    }

    @Test
    @DisplayName("AC-02.01.3 an exam date beyond 12 months is a business rule violation")
    void invalidExamDate() throws Exception {
        mvc.perform(put("/api/v1/me/onboarding")
                        .with(student(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"examBooked\":true,\"examDate\":\""
                                + LocalDate.now().plusDays(400) + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://toefly.app/problems/business-rule-violated"))
                .andExpect(jsonPath("$.rule").value("exam-date"))
                .andExpect(jsonPath("$.detail").value("Pick a date within the next 12 months"));
    }

    @Test
    @DisplayName("AC-02.03.3 preferences need If-Match and publish preferences-updated")
    void preferencesUseETags() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = "{\"timezone\":\"Asia/Tokyo\",\"explanationLanguage\":\"en\",\"minutesPerDay\":30,"
                + "\"preferredStudyTime\":\"20:00\"}";

        mvc.perform(put("/api/v1/me/preferences")
                        .with(student(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(put("/api/v1/me/preferences")
                        .with(student(userId))
                        .header("If-Match", "\"9\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.currentVersion").value(0));
        mvc.perform(put("/api/v1/me/preferences")
                        .with(student(userId))
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));

        assertThat(events("toefly.profile.preferences-updated.v1", userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-31.01.3 the AI processing consent is recorded and published")
    void consentChange() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(put("/api/v1/me/consents/AI_PROCESSING")
                        .with(student(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("WEB"))
                .andExpect(jsonPath("$.policyVersion").value("2026-11-01"));
        mvc.perform(get("/api/v1/me/consents").with(student(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4));
    }

    @Test
    @DisplayName("AC-31.03.3 a second export within 24 hours is rejected")
    void exportOncePerDay() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/v1/me/data-exports")
                        .with(student(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
        mvc.perform(post("/api/v1/me/data-exports")
                        .with(student(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    @DisplayName("AC-31.04.1 deletion publishes account-deletion-requested and disables the account")
    void accountDeletion() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/v1/me/deletion-request")
                        .with(student(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        assertThat(events("toefly.profile.account-deletion-requested.v1", userId))
                .isEqualTo(1);
        verify(identity).disableUser(userId);
        verify(sessions).revokeSessions(userId);

        mvc.perform(post("/api/v1/me/deletion-request")
                        .with(student(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://toefly.app/problems/account-pending-deletion"));
    }

    @Test
    @DisplayName("AC-01.05.4 sign out of all devices ends Keycloak and gateway sessions")
    void signOutEverywhere() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/v1/me/session-revocation").with(student(userId))).andExpect(status().isNoContent());

        verify(identity).endAllSessions(userId);
        verify(sessions).revokeSessions(userId);
    }

    @Test
    @DisplayName("POST /me/tutor-role adds TUTOR once; a second request is a conflict")
    void becomeTutor() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/v1/me/tutor-role")
                        .with(student(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptTutorTerms\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("STUDENT", "TUTOR")));
        verify(identity).addTutorRole(userId);

        mvc.perform(post("/api/v1/me/tutor-role")
                        .with(student(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptTutorTerms\":true}"))
                .andExpect(status().isConflict());
    }
}
