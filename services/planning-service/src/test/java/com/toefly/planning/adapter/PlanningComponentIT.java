package com.toefly.planning.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import com.toefly.events.assessment.SubmissionEvaluatedV1;
import com.toefly.events.common.AttemptMode;
import com.toefly.events.common.MistakeCategory;
import com.toefly.events.common.Section;
import com.toefly.events.practice.AttemptScoredV1;
import com.toefly.events.practice.AttemptStartedV1;
import com.toefly.events.profile.OnboardingCompletedV1;
import com.toefly.events.tutoragent.FocusItemV1;
import com.toefly.events.tutoragent.LearnerModelUpdatedV1;
import com.toefly.events.tutoragent.MistakeStatV1;
import com.toefly.events.tutoragent.MistakeStatus;
import com.toefly.events.tutoragent.MistakeTrend;
import com.toefly.events.tutoragent.ReviewMistakeV1;
import com.toefly.events.tutoragent.WeeklyReviewGeneratedV1;
import com.toefly.planning.application.port.in.BuildStudyPlanUseCase;
import com.toefly.planning.application.port.in.OnboardingGoal;
import com.toefly.planning.application.port.in.RolloverUseCase;
import com.toefly.planning.application.port.in.SyncLearnerUseCase;
import com.toefly.planning.application.port.in.TrackAttemptUseCase;
import com.toefly.planning.application.port.in.WeekTaskUseCase;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.PlanningEventPublisherPort;
import com.toefly.planning.application.port.out.UnitCatalogPort;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.config.ShiftableClock;
import com.toefly.planning.domain.composition.UnitRef;
import com.toefly.planning.domain.learner.LearnerModelView;
import com.toefly.planning.domain.learner.MistakeStat;
import com.toefly.planning.domain.week.DraftSource;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** planning-service against PostgreSQL, Kafka and Apicurio; content-service answers the units from fixtures. */
@SpringBootTest(properties = "toefly.planning.rollover.enabled=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import({
    PostgresTestcontainersConfiguration.class,
    KafkaTestcontainersConfiguration.class,
    ApicurioTestcontainersConfiguration.class,
    PlanningComponentIT.TestClock.class
})
class PlanningComponentIT {

    /** ai-gateway and the token endpoint of Keycloak. */
    static final WireMockServer DEPENDENCIES = new WireMockServer(options().dynamicPort());

    static {
        DEPENDENCIES.start();
    }

    @DynamicPropertySource
    static void dependencies(DynamicPropertyRegistry registry) {
        registry.add("toefly.planning.ai-gateway-url", DEPENDENCIES::baseUrl);
        registry.add(
                "spring.security.oauth2.client.provider.keycloak.token-uri", () -> DEPENDENCIES.baseUrl() + "/token");
    }

    @AfterAll
    static void stopDependencies() {
        DEPENDENCIES.stop();
    }

    static final JsonMapper JSON = JsonMapper.builder().build();
    /** A shared CI runner needs longer for the consumer group to settle on all topics after start. */
    static final Duration KAFKA_WAIT = Duration.ofSeconds(90);

    static final Path CONTRACTS = Path.of("src/test/resources/contracts");
    static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    static final List<TaskType> CLOSED = List.of(
            TaskType.COMPLETE_THE_WORDS,
            TaskType.READ_DAILY_LIFE,
            TaskType.READ_ACADEMIC_PASSAGE,
            TaskType.LISTEN_CHOOSE_RESPONSE,
            TaskType.LISTEN_CONVERSATION,
            TaskType.LISTEN_ANNOUNCEMENT,
            TaskType.LISTEN_ACADEMIC_TALK,
            TaskType.BUILD_A_SENTENCE);

    @TestConfiguration
    static class TestClock {
        @Bean
        @org.springframework.context.annotation.Primary
        ShiftableClock testClock() {
            return new ShiftableClock(Clock.systemUTC());
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ShiftableClock clock;

    @Autowired
    BuildStudyPlanUseCase plans;

    @Autowired
    TrackAttemptUseCase attempts;

    @Autowired
    WeekTaskUseCase tasks;

    @Autowired
    SyncLearnerUseCase sync;

    @Autowired
    RolloverUseCase rollover;

    @Autowired
    WeekRepository weeks;

    @Autowired
    PlanningEventPublisherPort publisher;

    @Autowired
    EventPayloadSerializer serializer;

    @Autowired
    KafkaContainer kafka;

    @Value("${toefly.events.registry-url}")
    String registryUrl;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    UnitCatalogPort units;

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
    void catalog() {
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo("/token"))
                .willReturn(okJson("{\"access_token\":\"svc\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        awaitListenerAssignment();
        clock.reset();
        when(units.roundUp()).thenReturn(units("Round-Up Unit"));
        when(units.collinsGrammar()).thenReturn(units("Collins Grammar Unit"));
        when(units.collinsVocabulary()).thenReturn(units("Collins Vocabulary Unit"));
        if (jdbc.sql("SELECT count(*) FROM item_set_catalog")
                        .query(Integer.class)
                        .single()
                == 0) {
            for (TaskType type : CLOSED) {
                for (int i = 0; i < 30; i++) {
                    sync.itemSetPublished(
                            UUID.randomUUID(),
                            1,
                            type,
                            "TOEFL_2026",
                            (i % 7 - 3) * 0.3,
                            3.0,
                            "topic " + i,
                            null,
                            Instant.now());
                }
            }
        }
        if (jdbc.sql("SELECT count(*) FROM item_set_catalog WHERE task_type = 'GRAMMAR_EXERCISE'")
                        .query(Integer.class)
                        .single()
                == 0) {
            for (int i = 0; i < 10; i++) {
                sync.itemSetPublished(
                        UUID.randomUUID(),
                        1,
                        TaskType.GRAMMAR_EXERCISE,
                        "TOEFL_2026",
                        0,
                        8.0,
                        "articles " + i,
                        null,
                        Instant.now());
            }
        }
    }

    private static List<UnitRef> units(String prefix) {
        return IntStream.rangeClosed(1, 20)
                .mapToObj(i -> new UnitRef(
                        UUID.nameUUIDFromBytes((prefix + i).getBytes(StandardCharsets.UTF_8)),
                        prefix + " " + i + ": topic"))
                .toList();
    }

    // ---- events ----

    @Test
    @DisplayName("AC-04.01.1, contract onboarding_completed: the onboarding from Kafka builds the plan and Week 1")
    void onboardingFromKafka() throws Exception {
        UUID user = UUID.randomUUID();
        OnboardingCompletedV1 event = onboarding(user);
        send("toefly.profile.onboarding-completed.v1", user.toString(), event.getEventId(), event);
        send("toefly.profile.onboarding-completed.v1", user.toString(), event.getEventId(), event);

        Awaitility.await()
                .atMost(KAFKA_WAIT)
                .untilAsserted(() -> assertThat(planCount(user)).isEqualTo(1));
        mvc.perform(get("/api/v1/study-plans/current").with(student(user)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.weeksCount").value(8))
                .andExpect(jsonPath("$.timezone").value("Europe/Minsk"));
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE partition_key = :user AND topic = :topic")
                        .param("user", user.toString())
                        .param("topic", "toefly.planning.week-published.v1")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "phase 4 DoD: attempt-scored with weekTaskId marks the task done and publishes week-task-completed once")
    void attemptScoredFromKafka() throws Exception {
        UUID user = plan();
        WeekTask task = firstOf(user, WeekTaskKind.ITEM_SET);
        UUID attempt = UUID.randomUUID();

        AttemptStartedV1 started = AttemptStartedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setAttemptId(attempt)
                .setItemSetId(UUID.fromString(task.refId()))
                .setTaskType(com.toefly.events.common.TaskType.valueOf(
                        task.taskType().name()))
                .setSection(Section.valueOf(task.section().name()))
                .setMode(AttemptMode.PRACTICE)
                .setWeekTaskId(task.id())
                .setItemSetVersion(1)
                .setStartedAt(Instant.now())
                .setItemCount(2)
                .setEstimatedSeconds(180)
                .setRetry(false)
                .build();
        send("toefly.practice.attempt-started.v1", user.toString(), started.getEventId(), started);
        Awaitility.await()
                .atMost(KAFKA_WAIT)
                .untilAsserted(() -> assertThat(taskStatus(task.id())).isEqualTo("IN_PROGRESS"));

        AttemptScoredV1 scored = scored(user, task, attempt, UUID.randomUUID());
        send("toefly.practice.attempt-scored.v1", user.toString(), scored.getEventId(), scored);
        send("toefly.practice.attempt-scored.v1", user.toString(), scored.getEventId(), scored);
        AttemptScoredV1 redelivered = scored(user, task, attempt, UUID.randomUUID());
        send("toefly.practice.attempt-scored.v1", user.toString(), redelivered.getEventId(), redelivered);

        Awaitility.await()
                .atMost(KAFKA_WAIT)
                .untilAsserted(() -> assertThat(taskStatus(task.id())).isEqualTo("DONE"));
        Awaitility.await()
                .atMost(KAFKA_WAIT)
                .untilAsserted(() -> assertThat(jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                                .param("id", redelivered.getEventId())
                                .query(Integer.class)
                                .single())
                        .isEqualTo(1));
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM outbox_event
                        WHERE topic = 'toefly.planning.week-task-completed.v1' AND subject LIKE :subject
                        """)
                        .param("subject", "%/tasks/" + task.id())
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
        mvc.perform(get("/api/v1/weeks/" + weekOf(user).id()).with(student(user)))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.progressPercent").isNumber());
    }

    @Test
    @DisplayName("docs/12 5c decision 1: learner-model-updated keeps the newest learner model once per event")
    void learnerModelFromKafka() throws Exception {
        UUID user = UUID.randomUUID();
        LearnerModelUpdatedV1 newer = learnerModel(user, 7, 4);
        LearnerModelUpdatedV1 older = learnerModel(user, 6, 1);
        send("toefly.tutor-agent.learner-model-updated.v1", user.toString(), newer.getEventId(), newer);
        send("toefly.tutor-agent.learner-model-updated.v1", user.toString(), newer.getEventId(), newer);
        send("toefly.tutor-agent.learner-model-updated.v1", user.toString(), older.getEventId(), older);

        Awaitility.await()
                .atMost(KAFKA_WAIT)
                .untilAsserted(() -> assertThat(jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                                .param("id", older.getEventId())
                                .query(Integer.class)
                                .single())
                        .isEqualTo(1));
        assertThat(jdbc.sql("""
                        SELECT version || ':' || (mistake_stats -> 0 ->> 'subcategory') || ':'
                            || (mistake_stats -> 0 ->> 'occurrencesLast7Days') || ':' || (bands ->> 'WRITING')
                        FROM learner_model_view WHERE user_id = :user
                        """).param("user", user).query(String.class).single())
                .isEqualTo("7:GRAMMAR.ARTICLES:4:3.5");
    }

    static LearnerModelUpdatedV1 learnerModel(UUID user, long version, int articles) {
        return LearnerModelUpdatedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setVersion(version)
                .setUpdatedAt(Instant.now())
                .setMistakeStats(List.of(
                        MistakeStatV1.newBuilder()
                                .setCategory(MistakeCategory.GRAMMAR)
                                .setSubcategory("ARTICLES")
                                .setWeight(3.2)
                                .setErrorRate(0.3)
                                .setPriority(2.4)
                                .setTrend(MistakeTrend.WORSENING)
                                .setStatus(MistakeStatus.ACTIVE)
                                .setOccurrencesLast7Days(articles)
                                .setUnitRefs(List.of("ROUNDUP5.U10", "COLLINS_GR.U02"))
                                .build(),
                        MistakeStatV1.newBuilder()
                                .setCategory(MistakeCategory.TASK_RESPONSE)
                                .setSubcategory("TASK_RESPONSE.ELABORATION")
                                .setWeight(1.1)
                                .setErrorRate(0.2)
                                .setPriority(0.9)
                                .setTrend(MistakeTrend.STABLE)
                                .setStatus(MistakeStatus.WATCH)
                                .setOccurrencesLast7Days(2)
                                .setUnitRefs(List.of())
                                .build()))
                .setBands(Map.of("WRITING", 3.5, "OVERALL", 4.0))
                .build();
    }

    // ---- WeekGenerator (docs/06-ai-agent.md §5.2) ----

    /** Week 1 done and closed: Week 2 of the rules waits; the learner model has recurring article mistakes. */
    private UUID closedWeekOne() {
        UUID user = plan();
        for (WeekTask task : weekOf(user).tasks()) {
            if (task.kind() == WeekTaskKind.ITEM_SET) {
                attempts.attemptScored(
                        user, task.id(), UUID.randomUUID(), task.taskType(), 2, 2, true, Instant.now(), Cause.none());
            } else {
                tasks.complete(user, task.id());
            }
        }
        tasks.finishEarly(user, weekOf(user).id());
        sync.learnerModelUpdated(new LearnerModelView(
                user,
                1,
                Instant.now(),
                List.of(new MistakeStat("GRAMMAR", "ARTICLES", 2.0, "ACTIVE", 4, List.of("ROUNDUP5.U10"))),
                Map.of("WRITING", 3.5)));
        return user;
    }

    private void sendReview(UUID user, UUID reviewId) throws Exception {
        WeeklyReviewGeneratedV1 review = WeeklyReviewGeneratedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setReviewId(reviewId)
                .setWeekId(week(user, 1).id())
                .setWeekNo(1)
                .setGeneratedAt(Instant.now())
                .setLanguage("EN")
                .setAiFallback(false)
                .setLowActivity(false)
                .setTopMistakes(List.of(ReviewMistakeV1.newBuilder()
                        .setCategory(MistakeCategory.GRAMMAR)
                        .setSubcategory("GRAMMAR.ARTICLES")
                        .setCount(4)
                        .build()))
                .setNextWeekFocus(List.of(FocusItemV1.newBuilder()
                        .setSubcategory("GRAMMAR.ARTICLES")
                        .setWhy("Articles were your most frequent mistake.")
                        .build()))
                .build();
        send("toefly.tutor-agent.weekly-review-generated.v1", user.toString(), review.getEventId(), review);
        send("toefly.tutor-agent.weekly-review-generated.v1", user.toString(), review.getEventId(), review);
    }

    private Week week(UUID user, int weekNo) {
        UUID plan = jdbc.sql("SELECT id FROM study_plan WHERE user_id = :user")
                .param("user", user)
                .query(UUID.class)
                .single();
        return weeks.findByPlan(plan).stream()
                .filter(week -> week.weekNo() == weekNo)
                .findFirst()
                .orElseThrow();
    }

    private int published(UUID weekId) {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE topic = :topic AND subject = :subject")
                .param("topic", "toefly.planning.week-published.v1")
                .param("subject", "weeks/" + weekId)
                .query(Integer.class)
                .single();
    }

    private static String fixture(String name) throws java.io.IOException {
        try (var in = PlanningComponentIT.class.getResourceAsStream("/ai-fixtures/week-generator/" + name)) {
            return new String(java.util.Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("docs/12 5c decision 1, AC-14.05.2: weekly-review-generated publishes Week 2 drafted by WeekGenerator")
    void weeklyReviewDraftsNextWeek() throws Exception {
        UUID user = closedWeekOne();
        UUID reviewId = UUID.randomUUID();
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo("/internal/v1/ai/chat/completions"))
                .withRequestBody(matchingJsonPath("$.userId", WireMock.equalTo(user.toString())))
                .withRequestBody(matchingJsonPath("$.useCase", WireMock.equalTo("WEEK_GENERATION")))
                .withRequestBody(matchingJsonPath("$.promptKey", WireMock.equalTo("planning.week.generate")))
                .withRequestBody(
                        matchingJsonPath("$.idempotencyKey", WireMock.matching("WeekGenerator:.*:" + reviewId + ":1")))
                .willReturn(okJson(fixture("component-valid.json"))));
        UUID week2 = week(user, 2).id();

        sendReview(user, reviewId);

        Awaitility.await()
                .atMost(KAFKA_WAIT)
                .untilAsserted(() -> assertThat(week(user, 2).draft().source()).isEqualTo(DraftSource.AI));
        assertThat(week(user, 2).id()).isEqualTo(week2);
        assertThat(week(user, 2).draft().stamp()).isNotNull();
        assertThat(published(week2)).isEqualTo(2);
        mvc.perform(get("/api/v1/weeks/" + week2).with(student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.aiFallback").value(false))
                .andExpect(jsonPath("$.rationale").value(Matchers.startsWith("Use a, an and the")))
                .andExpect(jsonPath("$.sections[?(@.section == 'GRAMMAR')].tasks[?(@.taskType == 'GRAMMAR_EXERCISE')]"
                                + ".rationale")
                        .value("Because of mistakes in your writing"));
        assertThat(DEPENDENCIES.findAll(postRequestedFor(urlPathEqualTo("/internal/v1/ai/chat/completions"))
                        .withRequestBody(matchingJsonPath("$.userId", WireMock.equalTo(user.toString())))))
                .hasSize(1);
    }

    @Test
    @DisplayName("docs/06 §5.2: ai-gateway down, Week 2 falls back to GreedyWeekPacker with aiFallback = true")
    void weeklyReviewFallsBack() throws Exception {
        UUID user = closedWeekOne();
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo("/internal/v1/ai/chat/completions"))
                .withRequestBody(matchingJsonPath("$.userId", WireMock.equalTo(user.toString())))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"type\":\"https://toefly.app/problems/ai-unavailable\",\"status\":503}")));
        UUID week2 = week(user, 2).id();

        sendReview(user, UUID.randomUUID());

        Awaitility.await()
                .atMost(KAFKA_WAIT)
                .untilAsserted(() -> assertThat(week(user, 2).draft().source()).isEqualTo(DraftSource.AI_FALLBACK));
        assertThat(published(week2)).isEqualTo(2);
        mvc.perform(get("/api/v1/weeks/" + week2).with(student(user)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.aiFallback").value(true))
                .andExpect(jsonPath("$.rationale").value(Matchers.startsWith("Focus of the week: articles")));
    }

    // ---- REST ----

    @Test
    @DisplayName("docs/06 §4.5 assignExtraTask: addWeekTaskInternal is idempotent, limited to 3 and 501 for the tutor")
    void extraTasksOfTheAgent() throws Exception {
        UUID user = plan();
        Week week = weekOf(user);
        UUID key = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            sync.itemSetPublished(
                    i == 0 ? first : UUID.randomUUID(),
                    1,
                    TaskType.READ_DAILY_LIFE,
                    "TOEFL_2026",
                    0,
                    2.5,
                    "extra " + i,
                    null,
                    Instant.now());
        }
        String body = "{\"source\":\"TUTOR_AGENT\",\"itemSetId\":\"" + first
                + "\",\"day\":\"TODAY\",\"reason\":\"Articles again\"}";
        String taskId = null;
        for (int i = 0; i < 2; i++) {
            MvcResult result = mvc.perform(post("/internal/v1/weeks/" + week.id() + "/tasks")
                            .with(service())
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.addedBy").value("TUTOR_AGENT"))
                    .andExpect(jsonPath("$.rationale").value("Articles again"))
                    .andExpect(jsonPath("$.dayIndex").value(1))
                    .andReturn();
            String id = body(result).path("taskId").asString();
            assertThat(taskId == null || taskId.equals(id)).isTrue();
            taskId = id;
            if (i == 1) {
                assertThat(result.getResponse().getHeader("Idempotent-Replayed"))
                        .isEqualTo("true");
            }
        }
        for (int i = 0; i < 3; i++) {
            UUID set = jdbc.sql("SELECT item_set_id FROM item_set_catalog WHERE topic = :topic")
                    .param("topic", "extra " + (i + 1))
                    .query(UUID.class)
                    .list()
                    .getLast();
            var result = mvc.perform(post("/internal/v1/weeks/" + week.id() + "/tasks")
                    .with(service())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"source\":\"TUTOR_AGENT\",\"itemSetId\":\"" + set + "\",\"reason\":\"More\"}"));
            if (i < 2) {
                result.andExpect(status().isCreated());
            } else {
                result.andExpect(status().isConflict())
                        .andExpect(jsonPath("$.type").value("https://toefly.app/problems/extra-task-limit-reached"))
                        .andExpect(jsonPath("$.code").value("EXTRA_TASK_LIMIT_REACHED"));
            }
        }
        mvc.perform(get("/api/v1/weeks/" + week.id()).with(student(user)))
                .andExpect(jsonPath("$.sections[*].tasks[?(@.addedBy == 'TUTOR_AGENT')].taskId", hasSize(3)));
        mvc.perform(post("/internal/v1/weeks/" + UUID.randomUUID() + "/tasks")
                        .with(service())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
        mvc.perform(post("/internal/v1/weeks/" + week.id() + "/tasks")
                        .with(service())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"TUTOR","studentId":"%s","tutorId":"%s","title":"Pages 10-12",
                                 "estimatedMinutes":20,"task":{"kind":"ITEM_SET","weekSection":"READING","dayIndex":2,
                                 "itemSetId":"%s"}}
                                """.formatted(user, UUID.randomUUID(), first)))
                .andExpect(status().isNotImplemented());
        mvc.perform(post("/internal/v1/weeks/" + week.id() + "/tasks")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-04.02.1, AC-05.01.1: the week, the gallery and Today of the learner")
    void reads() throws Exception {
        UUID user = plan();
        Week week = weekOf(user);
        mvc.perform(get("/api/v1/weeks/" + week.id()).with(student(user)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + week.version() + "\""))
                .andExpect(jsonPath("$.weekNo").value(1))
                .andExpect(jsonPath("$.dayCount").value(7))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.sections[0].tasks[0].provenance").value("EXTRACTED"));
        mvc.perform(get("/api/v1/weeks/" + week.id())
                        .with(student(user))
                        .header("If-None-Match", "\"" + week.version() + "\""))
                .andExpect(status().isNotModified());
        mvc.perform(get("/api/v1/study-plans/current/weeks").with(student(user)))
                .andExpect(jsonPath("$.items.length()").value(8))
                .andExpect(jsonPath("$.items[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.items[1].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[3].mockExam").value(true));
        mvc.perform(get("/api/v1/today").with(student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekNo").value(1))
                .andExpect(jsonPath("$.dayIndex").value(1))
                .andExpect(jsonPath("$.overdue.length()").value(0))
                .andExpect(jsonPath("$.today.length()").isNumber());
    }

    @Test
    @DisplayName("AC-04.03.3, AC-04.03.5: mark a resource done, undo it, skip and move a task")
    void commands() throws Exception {
        UUID user = plan();
        WeekTask resource = firstOf(user, WeekTaskKind.EXTERNAL_RESOURCE);
        WeekTask set = firstOf(user, WeekTaskKind.ITEM_SET);
        UUID key = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/week-tasks/" + resource.id() + "/completion")
                            .with(student(user))
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DONE"));
        }
        mvc.perform(delete("/api/v1/week-tasks/" + resource.id() + "/completion")
                        .with(student(user)))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/v1/week-tasks/" + set.id() + "/completion")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEEK_TASK_NOT_COMPLETABLE"));
        mvc.perform(post("/api/v1/week-tasks/" + set.id() + "/skip")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.status").value("SKIPPED"));

        Week week = weekOf(user);
        WeekTask other = firstOf(user, WeekTaskKind.ITEM_SET);
        mvc.perform(patch("/api/v1/weeks/" + week.id() + "/tasks/" + other.id())
                        .with(student(user))
                        .header("If-Match", "\"" + (week.version() + 3) + "\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"dayIndex\":4}"))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(patch("/api/v1/weeks/" + week.id() + "/tasks/" + other.id())
                        .with(student(user))
                        .header("If-Match", "\"" + week.version() + "\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"dayIndex\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayIndex").value(4));
    }

    @Test
    @DisplayName("AC-04.05.4, AC-04.06.3: rebalance once per 48 hours; Finish week early needs 100%")
    void weekCommands() throws Exception {
        UUID user = plan();
        Week week = weekOf(user);
        clock.shift(Duration.ofDays(5));
        mvc.perform(post("/api/v1/weeks/" + week.id() + "/rebalance")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebalance.excludedTaskIds.length()").isNumber());
        mvc.perform(post("/api/v1/weeks/" + week.id() + "/rebalance")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.nextAllowedAt").isString());
        mvc.perform(post("/api/v1/weeks/" + week.id() + "/completion")
                        .with(student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEEK_NOT_COMPLETABLE"));
    }

    @Test
    @DisplayName("NFR-SEC-02: no token 401, another learner gets 404, the tutor operations are 501 for now")
    void access() throws Exception {
        UUID user = plan();
        Week week = weekOf(user);
        mvc.perform(get("/api/v1/today")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/weeks/" + week.id()).with(student(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/study-plans/current").with(student(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tutor/pending-approvals").with(JwtFixtures.withRoles(UUID.randomUUID(), "TUTOR")))
                .andExpect(status().isNotImplemented());
        mvc.perform(get("/internal/v1/weeks/" + week.id()).with(student(user))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("phase 4 demo: the nightly check after a moved clock closes Week 1 at day 10 and builds Week 2")
    void rollover() throws Exception {
        UUID user = plan();
        clock.shift(Duration.ofDays(10).plusHours(1));
        rollover.rolloverAll();
        mvc.perform(get("/api/v1/study-plans/current/weeks").with(student(user)))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[1].status").value("PUBLISHED"));
        assertThat(OutboxAssertions.published(jdbc, "toefly.planning.week-completed.v1"))
                .isNotEmpty();
    }

    @Test
    @DisplayName("US-09.03, docs/12 5b decision 14: an evaluated first version closes the Writing task of the week")
    void submissionEvaluatedClosesTask() throws Exception {
        UUID user = plan();
        WeekTask task = firstOf(user, WeekTaskKind.ITEM_SET);
        SubmissionEvaluatedV1 attempt2 = evaluated(user, task, 2);
        send("toefly.assessment.submission-evaluated.v1", user.toString(), attempt2.getEventId(), attempt2);
        SubmissionEvaluatedV1 draft = evaluated(user, task, 1);
        send("toefly.assessment.submission-evaluated.v1", user.toString(), draft.getEventId(), draft);
        send("toefly.assessment.submission-evaluated.v1", user.toString(), draft.getEventId(), draft);

        Awaitility.await()
                .atMost(KAFKA_WAIT)
                .untilAsserted(() -> assertThat(taskStatus(task.id())).isEqualTo("DONE"));
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM outbox_event
                        WHERE topic = 'toefly.planning.week-task-completed.v1' AND subject LIKE :subject
                        """)
                        .param("subject", "%/tasks/" + task.id())
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    private static SubmissionEvaluatedV1 evaluated(UUID user, WeekTask task, int versionNo) {
        return SubmissionEvaluatedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setSubmissionId(UUID.randomUUID())
                .setVersionNo(versionNo)
                .setEvaluationId(UUID.randomUUID())
                .setItemSetId(UUID.fromString(task.refId()))
                .setTaskType(com.toefly.events.common.TaskType.WRITE_ACADEMIC_DISCUSSION)
                .setSection(Section.WRITING)
                .setStatus(com.toefly.events.common.SubmissionStatus.EVALUATED)
                .setWeekTaskId(task.id())
                .setEvaluatedAt(Instant.now())
                .setRubricScore(4)
                .setPromptKey("writing.grade.discussion")
                .setPromptVersion(1)
                .setFinalScore(3.5)
                .build();
    }

    // ---- contracts ----

    @TestFactory
    @DisplayName("ADR-0031: planning events fulfil the contracts of their consumers")
    Stream<DynamicTest> eventContracts() {
        OutboxMessageReceiver receiver = new OutboxMessageReceiver(jdbc, registryUrl);
        return ContractTests.messages(CONTRACTS, this, receiver).onClose(receiver::close);
    }

    @TestFactory
    @DisplayName("ADR-0031: the internal API fulfils the contracts of tutor-agent and profile")
    Stream<DynamicTest> restContracts() {
        prepareContractData();
        return Stream.of("tutor-agent-service", "profile-service")
                .flatMap(caller -> ContractTests.rest(CONTRACTS.resolve(caller), MockMvcExchanger.of(mvc, service())));
    }

    @Test
    @DisplayName("docs/11 §8.5: every planning topic has a message contract")
    void contractsCoverTopics() {
        ContractCoverage.assertMessagesCover(CONTRACTS, publisher.topics());
    }

    void studyPlanCreated() {
        plan();
    }

    void weekDraftGenerated() {
        plan();
    }

    void weekPublished() {
        plan();
    }

    void weekTaskCompleted() {
        UUID user = plan();
        tasks.complete(user, firstOf(user, WeekTaskKind.EXTERNAL_RESOURCE).id());
    }

    void weekRebalanced() {
        UUID user = plan();
        WeekTask task = firstOf(user, WeekTaskKind.ITEM_SET);
        attempts.attemptStarted(user, task.id(), UUID.randomUUID(), false, Instant.now());
        clock.shift(Duration.ofDays(6));
        rollover.rolloverAll();
        clock.reset();
    }

    void weekCompleted() {
        UUID user = plan();
        for (WeekTask task : weekOf(user).tasks()) {
            if (task.kind() == WeekTaskKind.ITEM_SET) {
                attempts.attemptScored(
                        user, task.id(), UUID.randomUUID(), task.taskType(), 2, 2, true, Instant.now(), Cause.none());
            } else {
                tasks.complete(user, task.id());
            }
        }
        tasks.finishEarly(user, weekOf(user).id());
    }

    /** The fixed learner, week and item set of the REST contracts. */
    private void prepareContractData() {
        sync.itemSetPublished(
                UUID.fromString(ContractData.ITEM_SET),
                1,
                TaskType.READ_DAILY_LIFE,
                "TOEFL_2026",
                0,
                2.5,
                "a notice about the library",
                null,
                Instant.now());
        UUID owner = UUID.fromString(ContractData.STUDENT);
        if (planCount(owner) == 0) {
            plans.onboardingCompleted(goal(owner), Cause.none());
        }
        UUID fixed = UUID.fromString(ContractData.WEEK);
        if (jdbc.sql("SELECT count(*) FROM week WHERE id = :id")
                        .param("id", fixed)
                        .query(Integer.class)
                        .single()
                == 0) {
            UUID other = UUID.randomUUID();
            plans.onboardingCompleted(goal(other), Cause.none());
            UUID old = weekOf(other).id();
            jdbc.sql("UPDATE week SET week_no = 0 WHERE id = :old")
                    .param("old", old)
                    .update();
            jdbc.sql("""
                            INSERT INTO week (id, plan_id, user_id, week_no, status, day1, minutes_per_day, mock_exam,
                                weak_types, rationale, generated_at, published_at, rebalance_count, rebalance_skipped,
                                progress_percent, version)
                            SELECT :fixed, plan_id, user_id, 1, status, day1, minutes_per_day, mock_exam, weak_types,
                                rationale, generated_at, published_at, rebalance_count, rebalance_skipped,
                                progress_percent, version
                            FROM week WHERE id = :old
                            """).param("fixed", fixed).param("old", old).update();
            jdbc.sql("UPDATE week_task SET week_id = :fixed WHERE week_id = :old")
                    .param("fixed", fixed)
                    .param("old", old)
                    .update();
            jdbc.sql("DELETE FROM week WHERE id = :old").param("old", old).update();
        }
    }

    // ---- helpers ----

    private UUID plan() {
        UUID user = UUID.randomUUID();
        plans.onboardingCompleted(goal(user), Cause.none());
        return user;
    }

    private static OnboardingGoal goal(UUID user) {
        return new OnboardingGoal(user, 1, null, MINSK, 60, Map.of("OVERALL", 4.5));
    }

    private Week weekOf(UUID user) {
        UUID plan = jdbc.sql("SELECT id FROM study_plan WHERE user_id = :user")
                .param("user", user)
                .query(UUID.class)
                .single();
        return weeks.findByPlan(plan).stream()
                .filter(week -> week.status().open())
                .findFirst()
                .orElseThrow();
    }

    private WeekTask firstOf(UUID user, WeekTaskKind kind) {
        return weekOf(user).tasks().stream()
                .filter(task -> task.kind() == kind && task.status().open())
                .findFirst()
                .orElseThrow();
    }

    private int planCount(UUID user) {
        return jdbc.sql("SELECT count(*) FROM study_plan WHERE user_id = :user")
                .param("user", user)
                .query(Integer.class)
                .single();
    }

    private String taskStatus(UUID task) {
        return jdbc.sql("SELECT status FROM week_task WHERE id = :id")
                .param("id", task)
                .query(String.class)
                .single();
    }

    private static OnboardingCompletedV1 onboarding(UUID user) {
        return OnboardingCompletedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setRevision(1)
                .setOverallTarget(4.5)
                .setExamBooked(false)
                .setExamDate(null)
                .setTimezone("Europe/Minsk")
                .setExplanationLanguage("en")
                .setMinutesPerDay(60)
                .setPreferredStudyTime("19:00")
                .build();
    }

    private static AttemptScoredV1 scored(UUID user, WeekTask task, UUID attempt, UUID eventId) {
        return AttemptScoredV1.newBuilder()
                .setEventId(eventId)
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setAttemptId(attempt)
                .setItemSetId(UUID.fromString(task.refId()))
                .setTaskType(com.toefly.events.common.TaskType.valueOf(
                        task.taskType().name()))
                .setSection(Section.valueOf(task.section().name()))
                .setMode(AttemptMode.PRACTICE)
                .setWeekTaskId(task.id())
                .setScoredAt(Instant.now())
                .setCorrectCount(1)
                .setTotalCount(2)
                .setUnansweredCount(0)
                .setActiveSeconds(120)
                .setEstimatedSeconds(180)
                .setTimingMultiplier(1.0)
                .setThetaBefore(0.0)
                .setUnaided(true)
                .setItems(List.of())
                .build();
    }

    private void send(String topic, String key, UUID eventId, SpecificRecord event) throws Exception {
        EventPayloadSerializer.SerializedPayload payload = serializer.serialize(topic, event);
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (var producer = new KafkaProducer<>(properties, new StringSerializer(), new ByteArraySerializer())) {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload.bytes());
            record.headers().add(CloudEventHeaders.ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("ce_type", topic.getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }

    private static JwtRequestPostProcessor student(UUID user) {
        return JwtFixtures.student(user);
    }

    private static JwtRequestPostProcessor service() {
        return JwtFixtures.withRoles(UUID.randomUUID(), "SERVICE");
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    /** Fixed identifiers used by the REST contracts. */
    static final class ContractData {
        static final String STUDENT = "7d0d2c6a-6b1f-4a47-9f0e-2f4a3e9b5c21";
        static final String WEEK = "5b1e9c3a-2f0d-4c8e-9a7b-1d2c3e4f5a6b";
        static final String ITEM_SET = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f";

        private ContractData() {}
    }
}
