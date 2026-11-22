package com.toefly.assessment.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.toefly.assessment.application.port.in.DeferredEvaluationUseCase;
import com.toefly.assessment.application.port.in.TutorLinkUseCase;
import com.toefly.assessment.application.port.out.AssessmentEventPublisherPort;
import com.toefly.common.events.CloudEventHeaders;
import com.toefly.common.events.EventPayloadSerializer;
import com.toefly.common.test.ApicurioTestcontainersConfiguration;
import com.toefly.common.test.JwtFixtures;
import com.toefly.common.test.KafkaTestcontainersConfiguration;
import com.toefly.common.test.OutboxAssertions;
import com.toefly.common.test.PostgresTestcontainersConfiguration;
import com.toefly.common.test.contract.ContractCoverage;
import com.toefly.common.test.contract.ContractTests;
import com.toefly.common.test.contract.MockMvcExchanger;
import com.toefly.common.test.contract.OutboxMessageReceiver;
import com.toefly.events.assessment.EvaluationRequestedV1;
import com.toefly.events.common.AttemptMode;
import com.toefly.events.common.Section;
import com.toefly.events.common.TaskType;
import com.toefly.events.practice.AbilityUpdatedV1;
import com.toefly.events.practice.AttemptScoredV1;
import com.toefly.events.practice.ItemResultV1;
import com.toefly.events.profile.OnboardingCompletedV1;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * assessment-service against PostgreSQL, Kafka and Apicurio. content-service and ai-gateway answer from WireMock with
 * the synthetic task and the LLM answers recorded from the live ai-gateway (docs/11 §7, docs/12 phase 5b
 * decision 5).
 */
@SpringBootTest(properties = "toefly.assessment.deferred.enabled=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import({
    PostgresTestcontainersConfiguration.class,
    KafkaTestcontainersConfiguration.class,
    ApicurioTestcontainersConfiguration.class,
    AssessmentComponentIT.TestClock.class
})
class AssessmentComponentIT {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Path FIXTURES = Path.of("src/test/resources/ai-fixtures");
    static final Path CONTRACTS = Path.of("src/test/resources/contracts");
    static final String ITEM_SET = "6f1e2d3c-4b5a-4c6d-8e7f-90a1b2c3d4e5";
    static final String CONTRACT_SUBMISSION = "0c7d3e2a-5b6f-4a1c-9d8e-2f3a4b5c6d7e";
    static final String REQUESTED = "toefly.assessment.evaluation-requested.v1";
    static final String EVALUATED = "toefly.assessment.submission-evaluated.v1";
    static final String ESTIMATE = "toefly.assessment.band-estimate-updated.v1";
    static final WireMockServer DEPENDENCIES = new WireMockServer(options().dynamicPort());

    static {
        DEPENDENCIES.start();
    }

    @DynamicPropertySource
    static void dependencies(DynamicPropertyRegistry registry) {
        registry.add("toefly.assessment.content-url", DEPENDENCIES::baseUrl);
        registry.add("toefly.assessment.ai-gateway-url", DEPENDENCIES::baseUrl);
        registry.add(
                "spring.security.oauth2.client.provider.keycloak.token-uri", () -> DEPENDENCIES.baseUrl() + "/token");
    }

    @AfterAll
    static void stop() {
        DEPENDENCIES.stop();
    }

    @TestConfiguration
    static class TestClock {
        static final MovableClock CLOCK = new MovableClock();

        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    /** System time that a test can move forward. */
    static final class MovableClock extends Clock {
        volatile Duration offset = Duration.ZERO;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.now().plus(offset);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EventPayloadSerializer serializer;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    DeferredEvaluationUseCase deferred;

    @Autowired
    TutorLinkUseCase tutorLinks;

    @Autowired
    AssessmentEventPublisherPort publisher;

    @Value("${toefly.events.registry-url}")
    String registryUrl;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    KafkaListenerEndpointRegistry listenerRegistry;

    /** Slow CI runners join the consumer groups late; the tests start once every listener owns its partitions. */
    private void awaitListenerAssignment() {
        Awaitility.await()
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> listenerRegistry.getAllListenerContainers().stream()
                        .filter(MessageListenerContainer::isRunning)
                        .allMatch(c -> c.getAssignedPartitions() != null
                                && !c.getAssignedPartitions().isEmpty()));
    }

    @BeforeEach
    void stubs() throws Exception {
        awaitListenerAssignment();
        TestClock.CLOCK.offset = Duration.ZERO;
        DEPENDENCIES.resetAll();
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo("/token"))
                .willReturn(okJson("{\"access_token\":\"svc\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        DEPENDENCIES.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/item-sets/" + ITEM_SET))
                .willReturn(okJson(Files.readString(FIXTURES.resolve("discussion-item-set.json")))));
        DEPENDENCIES.stubFor(WireMock.get(urlPathMatching("/internal/v1/item-sets/.*"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(404)));
        recordedAnswers();
    }

    /** Passes A and B of the draft and of Attempt 2, recorded from the live ai-gateway. */
    private void recordedAnswers() throws Exception {
        for (int version = 1; version <= 2; version++) {
            for (String pass : List.of("A", "B")) {
                DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo("/internal/v1/ai/chat/completions"))
                        .withRequestBody(matchingJsonPath("$.passTag", WireMock.equalTo(pass)))
                        .withRequestBody(matchingJsonPath(
                                "$.idempotencyKey", WireMock.matching("WritingGrader:.*:" + version + ":" + pass)))
                        .willReturn(okJson(Files.readString(recorded(version, pass)))));
            }
        }
    }

    private static Path recorded(int version, String pass) throws Exception {
        try (Stream<Path> files = Files.list(FIXTURES.resolve("recorded"))) {
            return files.filter(f -> f.getFileName().toString().endsWith("_" + version + "_" + pass + ".json"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    // ---- the Writing loop ----

    @Test
    @DisplayName("AC-09.03.4, AC-11.03.1, AC-11.03.2, AC-11.05.2: submit → Kafka worker → review → Attempt 2 grows")
    void writingLoop() throws Exception {
        UUID user = UUID.randomUUID();
        String id = create(user);
        String draft = essay(1);
        save(user, id, draft, 0);
        Instant submitted = Instant.now();
        submit(user, id, 1);

        assertThat(published(REQUESTED, id)).hasSize(1);
        deliver(id, 1, 1);
        awaitStatus(user, id, "EVALUATED");
        Duration took = Duration.between(submitted, Instant.now());
        assertThat(took)
                .as("submit → evaluated with recorded answers (SLO 95%% < 60 s)")
                .isLessThan(Duration.ofSeconds(60));

        JsonNode v1 = body(
                mvc.perform(get("/api/v1/submissions/" + id + "/versions/1").with(student(user)))
                        .andExpect(status().isOk())
                        .andReturn());
        double first = v1.path("evaluation").path("finalScore").asDouble();
        assertThat(first).isBetween(0.0, 5.0);
        assertThat(v1.path("evaluation").path("criteria")).hasSize(6);
        JsonNode annotations = v1.path("evaluation").path("annotations");
        assertThat(annotations).isNotEmpty();
        for (JsonNode a : annotations) {
            if (!a.path("start").isNull()) {
                assertThat(draft.substring(
                                a.path("start").asInt(), a.path("end").asInt()))
                        .isEqualTo(a.path("anchorQuote").asString());
            }
        }
        assertThat(published(EVALUATED, id)).hasSize(1);
        assertThat(OutboxAssertions.published(jdbc, ESTIMATE)).isNotEmpty();

        MvcResult stream = mvc.perform(
                        get("/api/v1/submissions/" + id + "/events").with(student(user)))
                .andExpect(request().asyncStarted())
                .andReturn();
        stream.getAsyncResult(10_000);
        assertThat(stream.getResponse().getContentAsString()).contains("evaluation.completed");

        mvc.perform(post("/api/v1/submissions/" + id + "/versions")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.text").value(draft));
        save(user, id, essay(2), version(user, id));
        submit(user, id, 2);
        deliver(id, 2, 1);
        awaitStatus(user, id, "EVALUATED");

        JsonNode submission = fetch(user, id);
        JsonNode v2 = submission.path("versions").path(1);
        assertThat(v2.path("countsForEstimate").asBoolean()).isFalse();
        assertThat(v2.path("effectiveScore").asDouble()).isGreaterThan(first);
        assertThat(v2.path("scoreDelta").asDouble()).isPositive();
        assertThat(submission.path("versions").path(0).path("evaluation").path("annotations"))
                .anyMatch(a -> a.path("status").asString().equals("FIXED"));
    }

    @Test
    @DisplayName("AC-11.06.1, AC-11.06.2: AI unavailable defers the version and the retry evaluates it")
    void deferredWhenAiIsDown() throws Exception {
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo("/internal/v1/ai/chat/completions"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "30")
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(
                                "{\"type\":\"https://toefly.app/problems/ai-provider-unavailable\",\"status\":503}")));
        UUID user = UUID.randomUUID();
        String id = create(user);
        save(user, id, essay(1), 0);
        submit(user, id, 1);
        deliver(id, 1, 1);
        awaitStatus(user, id, "EVALUATION_DEFERRED");
        JsonNode submission = fetch(user, id);
        assertThat(submission.path("deferred").path("attempts").asInt()).isEqualTo(1);
        assertThat(submission.path("versions").path(0).path("text").asString()).isEqualTo(essay(1));

        mvc.perform(get("/api/v1/admin/deferred-evaluations").with(student(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/deferred-evaluations").with(JwtFixtures.withRoles(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.submissionId == '" + id + "')].attempts")
                        .value(1));

        DEPENDENCIES.resetAll();
        stubs();
        TestClock.CLOCK.offset = Duration.ofMinutes(2);
        assertThat(deferred.retryDue()).isGreaterThanOrEqualTo(1);
        assertThat(published(REQUESTED, id)).hasSize(2);
        deliver(id, 1, 2);
        awaitStatus(user, id, "EVALUATED");
        assertThat(jdbc.sql("SELECT count(*) FROM deferred_evaluation WHERE submission_id = :id::uuid")
                        .param("id", id)
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("AC-11.05.3, INV-SUB-06, docs/05 §2.7: guards of the API")
    void guards() throws Exception {
        UUID user = UUID.randomUUID();
        String id = create(user);
        mvc.perform(post("/api/v1/submissions/" + id + "/submission")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_READY"));
        mvc.perform(put("/api/v1/submissions/" + id + "/versions/current")
                        .with(student(user))
                        .header("If-Match", "\"99\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\",\"elapsedSeconds\":1}"))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(put("/api/v1/submissions/" + id + "/versions/current")
                        .with(student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\",\"elapsedSeconds\":1}"))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(get("/api/v1/submissions/" + id).with(student(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/submissions/" + id + "/versions")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/submissions")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemSetId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_SET_UNAVAILABLE"));
        mvc.perform(get("/api/v1/submissions?limit=1&weekTaskId=" + UUID.randomUUID())
                        .with(student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/submissions?limit=1").with(student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(get("/api/v1/submissions?cursor=broken").with(student(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC-11.09.1: a linked tutor scores the draft; the version becomes TUTOR_REVIEWED")
    void tutorReview() throws Exception {
        UUID user = UUID.randomUUID();
        UUID tutor = UUID.randomUUID();
        String id = create(user);
        save(user, id, essay(1), 0);
        submit(user, id, 1);
        deliver(id, 1, 1);
        awaitStatus(user, id, "EVALUATED");
        String review = "{\"versionNo\":1,\"score\":4.5,\"criteria\":{\"development\":4},\"note\":\"Good example.\"}";
        mvc.perform(put("/api/v1/submissions/" + id + "/tutor-evaluation")
                        .with(JwtFixtures.withRoles(tutor, "TUTOR"))
                        .header("If-Match", "\"" + version(user, id) + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(review))
                .andExpect(status().isNotFound());
        tutorLinks.enrolled(tutor, user);
        mvc.perform(put("/api/v1/submissions/" + id + "/tutor-evaluation")
                        .with(JwtFixtures.withRoles(tutor, "TUTOR"))
                        .header("If-Match", "\"" + version(user, id) + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(review))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluator").value("TUTOR"));
        mvc.perform(get("/api/v1/submissions/" + id + "/evaluations").with(JwtFixtures.withRoles(tutor, "TUTOR")))
                .andExpect(jsonPath("$.items.length()").value(2));
        assertThat(fetch(user, id).path("status").asString()).isEqualTo("TUTOR_REVIEWED");
        assertThat(fetch(user, id)
                        .path("versions")
                        .path(0)
                        .path("effectiveScore")
                        .asDouble())
                .isEqualTo(4.5);
    }

    @Test
    @DisplayName("AC-13.01.2: θ, Build a Sentence and onboarding from Kafka recompute the estimate")
    void estimateFromKafka() throws Exception {
        UUID user = UUID.randomUUID();
        OnboardingCompletedV1 onboarding = onboarding(user, Instant.now().minus(Duration.ofDays(30)));
        send("toefly.profile.onboarding-completed.v1", user, onboarding.getEventId(), onboarding);
        AbilityUpdatedV1 ability = ability(user);
        send("toefly.practice.ability-updated.v1", user, ability.getEventId(), ability);
        send("toefly.practice.ability-updated.v1", user, ability.getEventId(), ability);
        AttemptScoredV1 bas = buildSentence(user);
        send("toefly.practice.attempt-scored.v1", user, bas.getEventId(), bas);
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> mvc.perform(get("/api/v1/band-estimates/current").with(student(user)))
                                .andExpect(jsonPath("$.sections[?(@.section == 'READING')].status")
                                        .value("READY"))
                                .andExpect(jsonPath("$.overall").doesNotExist()));
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(jdbc.sql(
                                        "SELECT jsonb_array_length(correct) FROM build_sentence_result WHERE user_id = :u")
                                .param("u", user)
                                .query(Integer.class)
                                .list())
                        .containsExactly(10));
        mvc.perform(get("/api/v1/band-estimates").with(student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].estimate.sections.length()").value(4));
    }

    // ---- internal reads for tutor-agent-service (docs/12 phase 5c, decision 2) ----

    @Test
    @DisplayName("getSubmissionInternal: the model answer appears once the version is evaluated; owner or service only")
    void internalSubmission() throws Exception {
        UUID user = UUID.randomUUID();
        String id = create(user);
        save(user, id, essay(1), 0);
        submit(user, id, 1);
        mvc.perform(get("/internal/v1/submissions/" + id).with(service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EVALUATING"))
                .andExpect(jsonPath("$.modelAnswer").doesNotExist());
        deliver(id, 1, 1);
        awaitStatus(user, id, "EVALUATED");

        JsonNode internal =
                body(mvc.perform(get("/internal/v1/submissions/" + id).with(student(user)))
                        .andExpect(status().isOk())
                        .andReturn());
        assertThat(internal.path("submissionId").asString()).isEqualTo(id);
        assertThat(internal.path("taskType").asString()).isEqualTo("WRITE_ACADEMIC_DISCUSSION");
        JsonNode v1 = internal.path("versions").path(0);
        assertThat(v1.path("text").asString()).isEqualTo(essay(1));
        assertThat(v1.path("tutorReviewed").asBoolean()).isFalse();
        assertThat(v1.path("evaluatedAt").asString()).isNotBlank();
        assertThat(v1.path("evaluation").path("criteria")).hasSize(6);
        assertThat(v1.path("evaluation").path("comments")).isNotEmpty();
        assertThat(v1.path("evaluation").path("comments").path(0).path("quote").asString())
                .isNotBlank();
        assertThat(internal.path("modelAnswer").path("source").asString()).isEqualTo("GENERATED");
        assertThat(internal.path("modelAnswer").path("text").asString()).startsWith("I agree with Felix");

        mvc.perform(get("/internal/v1/submissions/" + id + "?version=2").with(service()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/v1/submissions/" + id).with(student(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/v1/submissions/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getBandEstimateInternal: the service token or the learner's own JWT; another user is 403")
    void internalBandEstimate() throws Exception {
        UUID user = UUID.randomUUID();
        mvc.perform(get("/internal/v1/users/" + user + "/band-estimate").with(service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(4))
                .andExpect(jsonPath("$.needsMoreData.length()").value(4));
        mvc.perform(get("/internal/v1/users/" + user + "/band-estimate").with(student(user)))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/v1/users/" + user + "/band-estimate").with(student(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/v1/users/" + user + "/export").with(student(user)))
                .andExpect(status().isForbidden());
    }

    // ---- contracts (ADR-0031) ----

    @TestFactory
    @DisplayName("ADR-0031: the internal API fulfils the contracts of tutor-agent-service")
    Stream<DynamicTest> restContracts() {
        prepareContractData();
        return ContractTests.rest(CONTRACTS.resolve("tutor-agent-service"), MockMvcExchanger.of(mvc, service()));
    }

    /** An evaluated draft with a fixed id for the contract get_submission_internal. */
    private void prepareContractData() {
        UUID submission = UUID.fromString(CONTRACT_SUBMISSION);
        if (jdbc.sql("SELECT count(*) FROM submission WHERE id = :id")
                        .param("id", submission)
                        .query(Integer.class)
                        .single()
                > 0) {
            return;
        }
        UUID user = UUID.fromString("5d2e8f10-3a4b-4c5d-9e6f-7a8b9c0d1e2f");
        jdbc.sql("""
                        INSERT INTO submission (id, user_id, item_set_id, item_set_version, task_type, mode,
                            week_task_id, exam_session_id, status, current_version_no, version, created_at)
                        VALUES (:id, :user, :set::uuid, 1, 'WRITE_ACADEMIC_DISCUSSION', 'PRACTICE', NULL, NULL,
                            'EVALUATED', 1, 3, '2026-11-20T10:00:00Z')
                        """)
                .param("id", submission)
                .param("user", user)
                .param("set", ITEM_SET)
                .update();
        jdbc.sql("""
                        INSERT INTO submission_version (id, submission_id, version_no, status, body, word_count,
                            active_seconds, overtime_seconds, counts_for_estimate, submitted_at)
                        VALUES (:vid, :id, 1, 'EVALUATED', 'I think people lives longer in small towns.', 8, 540, 0,
                            true, '2026-11-20T10:09:00Z')
                        """).param("vid", UUID.randomUUID()).param("id", submission).update();
        UUID evaluation = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO evaluation (id, submission_id, version_no, evaluator, rubric_id, final_score,
                            overall_score, criteria, strengths, priorities, passes, agreement, needs_human_review,
                            caps_applied, integrity_flags, prompt_key, prompt_version, model_id, degraded, created_at)
                        VALUES (:eid, :id, 1, 'AI', 'ETS_DISCUSSION_V1', 3.5, 4,
                            '[{"criterion":"accuracy","level":3,"note":"Agreement errors."}]'::jsonb,
                            '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 'AGREED', false, '[]'::jsonb, '[]'::jsonb,
                            'writing.grade.discussion', 1, 'openai/gpt-oss-120b', false, '2026-11-20T10:10:00Z')
                        """).param("eid", evaluation).param("id", submission).update();
        jdbc.sql("""
                        INSERT INTO inline_annotation (id, evaluation_id, ordinal, range_start, range_end,
                            anchor_quote, category, subcategory, severity, message, suggestion, status)
                        VALUES (:aid, :eid, 0, 8, 20, 'people lives', 'GRAMMAR', 'GRAMMAR.SUBJECT_VERB_AGREEMENT',
                            'MAJOR', 'A plural subject takes live.', 'people live', 'OPEN')
                        """).param("aid", UUID.randomUUID()).param("eid", evaluation).update();
        jdbc.sql("""
                        INSERT INTO submission_model_answer (submission_id, body, source)
                        VALUES (:id, 'Small towns are calmer, so people live longer there.', 'GENERATED')
                        """).param("id", submission).update();
    }

    @TestFactory
    @DisplayName("ADR-0031: assessment events fulfil the contracts of their consumers")
    Stream<DynamicTest> eventContracts() {
        OutboxMessageReceiver receiver = new OutboxMessageReceiver(jdbc, registryUrl);
        return ContractTests.messages(CONTRACTS, this, receiver).onClose(receiver::close);
    }

    @Test
    @DisplayName("docs/11 §8.5: every assessment topic has a message contract")
    void contractsCoverTopics() {
        ContractCoverage.assertMessagesCover(CONTRACTS, List.of(REQUESTED, EVALUATED, ESTIMATE));
        assertThat(publisher).isNotNull();
    }

    void evaluationRequested() throws Exception {
        UUID user = UUID.randomUUID();
        String id = create(user);
        save(user, id, essay(1), 0);
        submit(user, id, 1);
    }

    void submissionEvaluated() throws Exception {
        UUID user = UUID.randomUUID();
        String id = create(user);
        save(user, id, essay(1), 0);
        submit(user, id, 1);
        deliver(id, 1, 1);
        awaitStatus(user, id, "EVALUATED");
    }

    void bandEstimateUpdated() throws Exception {
        UUID user = UUID.randomUUID();
        int before = OutboxAssertions.published(jdbc, ESTIMATE).size();
        AbilityUpdatedV1 ability = ability(user);
        send("toefly.practice.ability-updated.v1", user, ability.getEventId(), ability);
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> OutboxAssertions.published(jdbc, ESTIMATE).size() > before);
    }

    // ---- helpers ----

    private String essay(int version) throws Exception {
        JsonNode essays = JSON.readTree(Files.readString(FIXTURES.resolve("essays.json")));
        return essays.path("versions").path(version - 1).path("text").asString();
    }

    private String create(UUID user) throws Exception {
        return body(mvc.perform(post("/api/v1/submissions")
                                .with(student(user))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"itemSetId\":\"" + ITEM_SET + "\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(header().exists("ETag"))
                        .andExpect(jsonPath("$.status").value("DRAFT"))
                        .andReturn())
                .path("id")
                .asString();
    }

    private void save(UUID user, String id, String text, int version) throws Exception {
        mvc.perform(put("/api/v1/submissions/" + id + "/versions/current")
                        .with(student(user))
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(java.util.Map.of("text", text, "elapsedSeconds", 540))))
                .andExpect(status().isOk());
    }

    private void submit(UUID user, String id, int versionNo) throws Exception {
        mvc.perform(post("/api/v1/submissions/" + id + "/submission")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EVALUATING"))
                .andExpect(jsonPath("$.currentVersionNo").value(versionNo));
    }

    private int version(UUID user, String id) throws Exception {
        return fetch(user, id).path("version").asInt();
    }

    private JsonNode fetch(UUID user, String id) throws Exception {
        return body(mvc.perform(get("/api/v1/submissions/" + id).with(student(user)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void awaitStatus(UUID user, String id, String expected) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() ->
                        assertThat(fetch(user, id).path("status").asString()).isEqualTo(expected));
    }

    private List<OutboxAssertions.PublishedEvent> published(String topic, String submissionId) {
        List<OutboxAssertions.PublishedEvent> events = new ArrayList<>();
        for (var event : OutboxAssertions.published(jdbc, topic)) {
            if (("submissions/" + submissionId).equals(event.subject())) {
                events.add(event);
            }
        }
        return events;
    }

    /** What Debezium does in the cluster: the command of the outbox goes to Kafka. */
    private void deliver(String submissionId, int versionNo, int attemptNo) throws Exception {
        UUID user = jdbc.sql("SELECT user_id FROM submission WHERE id = :id::uuid")
                .param("id", submissionId)
                .query(UUID.class)
                .single();
        EvaluationRequestedV1 event = EvaluationRequestedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setSubmissionId(UUID.fromString(submissionId))
                .setVersionNo(versionNo)
                .setTaskType(TaskType.WRITE_ACADEMIC_DISCUSSION)
                .setRubricId("ETS_DISCUSSION_V1")
                .setMode(AttemptMode.PRACTICE)
                .setAttemptNo(attemptNo)
                .build();
        send(REQUESTED, user, event.getEventId(), event);
    }

    private void send(String topic, UUID key, UUID eventId, SpecificRecord event) throws Exception {
        EventPayloadSerializer.SerializedPayload payload = serializer.serialize(topic, event);
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (var producer = new KafkaProducer<>(properties, new StringSerializer(), new ByteArraySerializer())) {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key.toString(), payload.bytes());
            record.headers().add(CloudEventHeaders.ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("ce_type", topic.getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }

    private static OnboardingCompletedV1 onboarding(UUID user, Instant at) {
        return OnboardingCompletedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(at)
                .setUserId(user)
                .setRevision(1)
                .setOverallTarget(5.0)
                .setExamBooked(false)
                .setExamDate(null)
                .setTimezone("Europe/Minsk")
                .setExplanationLanguage("en")
                .setMinutesPerDay(60)
                .setPreferredStudyTime("19:00")
                .build();
    }

    private static AbilityUpdatedV1 ability(UUID user) {
        return AbilityUpdatedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setSection(Section.READING)
                .setAttemptId(UUID.randomUUID())
                .setThetaBefore(1.0)
                .setThetaAfter(1.3)
                .setStandardError(0.45)
                .setN(40)
                .build();
    }

    private static AttemptScoredV1 buildSentence(UUID user) {
        List<ItemResultV1> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(ItemResultV1.newBuilder()
                    .setItemId(UUID.randomUUID())
                    .setOrdinal(i + 1)
                    .setScore(i < 8 ? 1 : 0)
                    .setMaxScore(1)
                    .setB(0.0)
                    .setCorrect(i < 8)
                    .setAnswered(true)
                    .setChosenOptionIds(List.of())
                    .setMistakeCategory(null)
                    .build());
        }
        return AttemptScoredV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setAttemptId(UUID.randomUUID())
                .setItemSetId(UUID.randomUUID())
                .setTaskType(TaskType.BUILD_A_SENTENCE)
                .setSection(Section.WRITING)
                .setMode(AttemptMode.PRACTICE)
                .setScoredAt(Instant.now())
                .setCorrectCount(8)
                .setTotalCount(10)
                .setUnansweredCount(0)
                .setActiveSeconds(300)
                .setEstimatedSeconds(360)
                .setTimingMultiplier(1.0)
                .setThetaBefore(0.0)
                .setUnaided(true)
                .setItems(items)
                .build();
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .JwtRequestPostProcessor
            student(UUID user) {
        return JwtFixtures.student(user);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor service() {
        return JwtFixtures.withRoles(UUID.randomUUID(), "SERVICE");
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
