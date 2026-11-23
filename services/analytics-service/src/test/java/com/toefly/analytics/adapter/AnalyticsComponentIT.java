package com.toefly.analytics.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toefly.common.events.CloudEventHeaders;
import com.toefly.common.events.EventPayloadSerializer;
import com.toefly.common.test.ApicurioTestcontainersConfiguration;
import com.toefly.common.test.JwtFixtures;
import com.toefly.common.test.KafkaTestcontainersConfiguration;
import com.toefly.common.test.PostgresTestcontainersConfiguration;
import com.toefly.common.test.contract.Contract;
import com.toefly.common.test.contract.ContractTests;
import com.toefly.common.test.contract.Contracts;
import com.toefly.common.test.contract.MockMvcExchanger;
import com.toefly.events.aigateway.AiCallOutcome;
import com.toefly.events.aigateway.UsageRecordedV1;
import com.toefly.events.assessment.BandEstimateUpdatedV1;
import com.toefly.events.assessment.EstimateStatus;
import com.toefly.events.assessment.SectionEstimateV1;
import com.toefly.events.assessment.SubmissionEvaluatedV1;
import com.toefly.events.common.AiProvider;
import com.toefly.events.common.AttemptMode;
import com.toefly.events.common.Role;
import com.toefly.events.common.Section;
import com.toefly.events.common.SubmissionStatus;
import com.toefly.events.common.TaskType;
import com.toefly.events.common.WeekCloseReason;
import com.toefly.events.common.WeekSection;
import com.toefly.events.common.WeekStatus;
import com.toefly.events.common.WeekTaskKind;
import com.toefly.events.planning.PublishedWeekTaskV1;
import com.toefly.events.planning.StudyPlanCreatedV1;
import com.toefly.events.planning.WeekCompletedV1;
import com.toefly.events.planning.WeekPublishedV1;
import com.toefly.events.planning.WeekTaskCompletedV1;
import com.toefly.events.practice.AbilityUpdatedV1;
import com.toefly.events.practice.AttemptScoredV1;
import com.toefly.events.practice.AttemptStartedV1;
import com.toefly.events.practice.AttemptSubmittedV1;
import com.toefly.events.practice.TimeTrackedV1;
import com.toefly.events.profile.AccountDeletionRequestedV1;
import com.toefly.events.profile.OnboardingCompletedV1;
import com.toefly.events.profile.ProfileCreatedV1;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** analytics-service against PostgreSQL, Kafka and Apicurio: events in through Kafka, read models out through REST. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import({
    PostgresTestcontainersConfiguration.class,
    KafkaTestcontainersConfiguration.class,
    ApicurioTestcontainersConfiguration.class,
    AnalyticsComponentIT.Topics.class
})
class AnalyticsComponentIT {

    /** Every consumed topic of the service. */
    static final List<String> CONSUMED = List.of(
            "toefly.profile.profile-created.v1",
            "toefly.profile.onboarding-completed.v1",
            "toefly.profile.preferences-updated.v1",
            "toefly.profile.account-deletion-requested.v1",
            "toefly.practice.attempt-started.v1",
            "toefly.practice.attempt-submitted.v1",
            "toefly.practice.attempt-scored.v1",
            "toefly.practice.ability-updated.v1",
            "toefly.practice.time-tracked.v1",
            "toefly.planning.study-plan-created.v1",
            "toefly.planning.week-published.v1",
            "toefly.planning.week-task-completed.v1",
            "toefly.planning.week-rebalanced.v1",
            "toefly.planning.week-completed.v1",
            "toefly.assessment.band-estimate-updated.v1",
            "toefly.assessment.submission-evaluated.v1",
            "toefly.ai-gateway.usage-recorded.v1");

    /**
     * A fresh broker has none of the consumed topics; they are created with their retry and dead-letter topics before
     * the listeners start, so every container gets its partitions at once instead of after a metadata refresh.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        KafkaAdmin.NewTopics consumedTopics() {
            return new KafkaAdmin.NewTopics(CONSUMED.stream()
                    .flatMap(topic -> Stream.of(topic, topic + ".retry", topic + ".dlq"))
                    .map(topic ->
                            TopicBuilder.name(topic).partitions(3).replicas(1).build())
                    .toArray(NewTopic[]::new));
        }
    }

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Duration WAIT = Duration.ofSeconds(60);
    static final ZoneId MINSK = ZoneId.of("Europe/Minsk");

    /** Producers' contracts written for this consumer (ADR-0031, consumer side). */
    static final List<Path> CONTRACTS = List.of(
            Path.of("../practice-service/src/test/resources/contracts/analytics-service"),
            Path.of("../planning-service/src/test/resources/contracts/analytics-service"),
            Path.of("../ai-gateway/src/test/resources/contracts/analytics-service"));

    /** Every read model table, ordered so that two runs compare row by row. */
    static final List<String> READ_MODELS = List.of(
            "SELECT to_jsonb(t)::text FROM learner t ORDER BY user_id",
            "SELECT to_jsonb(t)::text FROM student_dashboard_view t ORDER BY user_id",
            "SELECT to_jsonb(t)::text FROM week_summary t ORDER BY week_id",
            "SELECT to_jsonb(t)::text FROM band_point t ORDER BY point_id",
            "SELECT to_jsonb(t)::text FROM activity_day t ORDER BY user_id, local_date",
            "SELECT user_id || ' ' || ARRAY(SELECT d FROM unnest(done_days) d ORDER BY d)::text || ' ' || as_of"
                    + " FROM streak_view ORDER BY user_id",
            "SELECT to_jsonb(t)::text FROM attempt_norm t ORDER BY attempt_id",
            "SELECT to_jsonb(t)::text FROM planned_task t ORDER BY week_task_id",
            "SELECT to_jsonb(t)::text FROM learner_milestone t ORDER BY user_id",
            "SELECT to_jsonb(t)::text FROM product_metrics_daily t ORDER BY day",
            "SELECT to_jsonb(t)::text FROM ai_usage_daily t ORDER BY day, provider, model, prompt_key");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EventPayloadSerializer serializer;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    KafkaListenerEndpointRegistry listenerRegistry;

    @MockitoBean
    JwtDecoder jwtDecoder;

    final UUID user = UUID.randomUUID();
    final UUID week = UUID.randomUUID();
    final UUID task1 = UUID.randomUUID();
    final UUID task2 = UUID.randomUUID();
    final LocalDate today = LocalDate.now(MINSK);
    final LocalDate day1 = today.minusDays(2);

    /** Slow CI runners join the consumer groups late; the tests start once every listener owns its partitions. */
    @BeforeEach
    void awaitListenerAssignment() {
        Awaitility.await()
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(unassignedListeners())
                        .as("listener containers without partitions")
                        .isEmpty());
    }

    private List<String> unassignedListeners() {
        return listenerRegistry.getAllListenerContainers().stream()
                .filter(MessageListenerContainer::isRunning)
                .filter(c -> c.getAssignedPartitions() == null
                        || c.getAssignedPartitions().isEmpty())
                .map(MessageListenerContainer::getListenerId)
                .sorted()
                .toList();
    }

    @Test
    @DisplayName(
            "AC-05.02.1, AC-05.02.3, AC-19.01.2, AC-19.02.1: Kafka events reach Home through the read model with asOf")
    void learnerJourney() throws Exception {
        journey(user);

        mvc.perform(get("/api/v1/dashboards/student").with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.toString()))
                .andExpect(jsonPath("$.timezone").value("Europe/Minsk"))
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.currentWeek.weekNo").value(1))
                .andExpect(jsonPath("$.currentWeek.day").value(3))
                .andExpect(jsonPath("$.bandEstimate.overall.point").value(4.5))
                .andExpect(jsonPath("$.bandEstimate.sections[0].status").value("READY"))
                .andExpect(jsonPath("$.theta[0].section").value("READING"))
                .andExpect(jsonPath("$.lastResults.length()").value(2))
                .andExpect(jsonPath("$.targets.overall").value(5.0))
                .andExpect(jsonPath("$.todayGoal.goalMet").value(true))
                .andExpect(jsonPath("$.streak.todayDone").value(true))
                .andExpect(jsonPath("$.streak.current").value(1))
                .andExpect(jsonPath("$.weekActiveSeconds").value(1500))
                .andExpect(jsonPath("$.weeks[0].overall.point").value(4.5))
                .andExpect(jsonPath("$.asOf").isNotEmpty());

        mvc.perform(get("/api/v1/streaks/me").with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayDone").value(true))
                .andExpect(jsonPath("$.days.length()").value(30))
                .andExpect(jsonPath("$.days[29].done").value(true))
                .andExpect(jsonPath("$.todayGoalProgress").value(1.0));
    }

    @Test
    @DisplayName("AC-16.02.1, AC-16.02.2: Progress → Time shows time by section and the pace against the ETS norm")
    void timeReport() throws Exception {
        journey(user);

        mvc.perform(get("/api/v1/time-reports").with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeconds").value(1500))
                .andExpect(jsonPath("$.plannedSeconds").value(3 * 20 * 60))
                .andExpect(jsonPath("$.bySection[0].section").value("READING"))
                .andExpect(jsonPath("$.byTaskType[0].taskType").value("READ_ACADEMIC_PASSAGE"))
                .andExpect(jsonPath("$.byTaskType[0].paceRatio").value(1.45))
                .andExpect(jsonPath("$.byTaskType[0].verdict").value("SLOWER"));

        mvc.perform(get("/api/v1/activity-days")
                        .param("from", day1.toString())
                        .param("to", today.plusDays(1).toString())
                        .with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].goalMet").value(true))
                .andExpect(jsonPath("$.items[0].tasksDone").value(1));
    }

    @Test
    @DisplayName("docs/01 §7: admins see the funnel, week completion and AI spend")
    void adminMetrics() throws Exception {
        journey(user);
        UUID admin = UUID.randomUUID();

        mvc.perform(get("/api/v1/admin/metrics/product").with(JwtFixtures.admin(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funnel.registered").isNumber())
                .andExpect(jsonPath("$.retention[1].name").value("D7"))
                .andExpect(jsonPath("$.daily").isArray());
        mvc.perform(get("/api/v1/admin/metrics/ai-usage").with(JwtFixtures.admin(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.calls").isNumber())
                .andExpect(jsonPath("$.byProvider[0].key").value("GROQ"));
    }

    @Test
    @DisplayName("ADR-0011, docs/12 phase 11 DoD: rebuilding every read model gives the incremental result")
    void rebuildMatchesIncremental() throws Exception {
        journey(user);
        UUID other = UUID.randomUUID();
        journey(other);
        closeEarlierWeek(other);
        List<List<String>> incremental = snapshot();
        UUID admin = UUID.randomUUID();

        for (String projection : List.of("student-dashboard", "activity", "product-metrics", "ai-usage")) {
            String body = mvc.perform(post("/api/v1/admin/projections/{projection}/rebuild", projection)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .with(JwtFixtures.admin(admin)))
                    .andExpect(status().isAccepted())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.projection").value(projection))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            String jobId = JSON.readTree(body).path("id").asString();
            Awaitility.await()
                    .atMost(WAIT)
                    .untilAsserted(() -> mvc.perform(get("/api/v1/admin/projections/rebuilds/{id}", jobId)
                                    .with(JwtFixtures.admin(admin)))
                            .andExpect(jsonPath("$.status").value("COMPLETED"))
                            .andExpect(jsonPath("$.eventsReplayed").isNumber()));
        }

        assertThat(snapshot()).isEqualTo(incremental);
    }

    @Test
    @DisplayName("INV-ANL-05: account deletion removes the learner from the read models and the journal")
    void accountDeletion() throws Exception {
        journey(user);

        send(
                "toefly.profile.account-deletion-requested.v1",
                AccountDeletionRequestedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(Instant.now())
                        .setUserId(user)
                        .setDeletionId(UUID.randomUUID())
                        .setRequestedAt(Instant.now())
                        .setLegalDeadlineAt(Instant.now().plus(Duration.ofDays(30)))
                        .setRoles(List.of(Role.STUDENT))
                        .build());

        Awaitility.await().atMost(WAIT).until(() -> count("event_journal", user) == 0);
        for (String table : List.of(
                "learner",
                "student_dashboard_view",
                "week_summary",
                "band_point",
                "activity_day",
                "streak_view",
                "planned_task",
                "attempt_norm",
                "learner_milestone")) {
            assertThat(count(table, user)).as(table).isZero();
        }
    }

    @Test
    @DisplayName("docs/05 §2.8, §3: roles, foreign learners, periods and idempotency keys are checked")
    void problems() throws Exception {
        UUID admin = UUID.randomUUID();
        mvc.perform(get("/api/v1/dashboards/student")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/metrics/product").with(JwtFixtures.student(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/dashboards/student")
                        .param("studentId", UUID.randomUUID().toString())
                        .with(JwtFixtures.tutor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/activity-days")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .with(JwtFixtures.student(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/admin/projections/ai-usage/rebuild").with(JwtFixtures.admin(admin)))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(get("/api/v1/admin/projections/rebuilds/{id}", UUID.randomUUID())
                        .with(JwtFixtures.admin(admin)))
                .andExpect(status().isNotFound());

        String key = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/admin/projections/ai-usage/rebuild")
                        .header("Idempotency-Key", key)
                        .with(JwtFixtures.admin(admin)))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/admin/projections/ai-usage/rebuild")
                        .header("Idempotency-Key", key)
                        .with(JwtFixtures.admin(admin)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        mvc.perform(post("/api/v1/admin/projections/activity/rebuild")
                        .header("Idempotency-Key", key)
                        .with(JwtFixtures.admin(admin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSE"));
    }

    @Test
    @DisplayName("docs/03 §6.15: the journal is partitioned by month ahead of time")
    void journalPartitions() {
        String month = "event_journal_y%04dm%02d".formatted(today.getYear(), today.getMonthValue());
        assertThat(jdbc.sql("SELECT to_regclass(:name) IS NOT NULL")
                        .param("name", month)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    // ---- internal reads for tutor-agent-service (docs/12 phase 5c, decision 2) ----

    @Test
    @DisplayName("getStudentDashboardInternal and getTimeReportInternal answer as the public reads; own JWT or service")
    void internalReads() throws Exception {
        journey(user);

        JsonNode dashboard =
                JSON.readTree(mvc.perform(get("/api/v1/dashboards/student").with(JwtFixtures.student(user)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode internal = JSON.readTree(
                mvc.perform(get("/internal/v1/users/" + user + "/dashboard").with(service()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(internal).isEqualTo(dashboard);
        mvc.perform(get("/internal/v1/users/" + user + "/dashboard").with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekActiveSeconds").value(1500));

        mvc.perform(get("/internal/v1/users/" + user + "/time-reports")
                        .param("period", "TODAY")
                        .with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(today.toString()))
                .andExpect(jsonPath("$.to").value(today.plusDays(1).toString()));
        mvc.perform(get("/internal/v1/users/" + user + "/time-reports").with(service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(day1.toString()))
                .andExpect(jsonPath("$.totalSeconds").value(1500));
        mvc.perform(get("/internal/v1/users/" + user + "/time-reports")
                        .param("period", "LAST_4_WEEKS")
                        .with(service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(today.minusDays(27).toString()))
                .andExpect(jsonPath("$.totalSeconds").value(1500));

        mvc.perform(get("/internal/v1/users/" + user + "/time-reports")
                        .param("period", "YESTERDAY")
                        .with(service()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/internal/v1/users/" + user + "/dashboard").with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/v1/users/" + user + "/dashboard")).andExpect(status().isUnauthorized());
    }

    // ---- producer contracts (ADR-0031) ----

    @TestFactory
    @DisplayName("ADR-0031: the internal API fulfils the contracts of tutor-agent-service")
    Stream<DynamicTest> restContracts() {
        return ContractTests.rest(
                Path.of("src/test/resources/contracts/tutor-agent-service"), MockMvcExchanger.of(mvc, service()));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor service() {
        return JwtFixtures.withRoles(UUID.randomUUID(), "SERVICE");
    }

    // ---- consumer contracts (ADR-0031) ----

    @TestFactory
    @DisplayName("ADR-0031: what producers promise analytics-service is consumed once, also when redelivered")
    Stream<DynamicTest> consumerContracts() throws Exception {
        Map<String, Function<Map<String, Object>, Consumed>> consumers = new LinkedHashMap<>();
        consumers.put("time_tracked", this::timeTrackedContract);
        consumers.put("attempt_submitted", this::attemptSubmittedContract);
        consumers.put("week_task_completed", this::weekTaskCompletedContract);
        consumers.put("usage_recorded", this::usageRecordedContract);
        List<Contract> contracts = new ArrayList<>();
        for (Path directory : CONTRACTS) {
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(f -> f.toString().endsWith(".yml"))
                        .sorted()
                        .forEach(f -> contracts.add(Contracts.parse(f)));
            }
        }
        assertThat(contracts).extracting(Contract::name).containsExactlyInAnyOrderElementsOf(consumers.keySet());
        return contracts.stream()
                .map(contract -> DynamicTest.dynamicTest(contract.name(), () -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body =
                            (Map<String, Object>) contract.outputMessage().body();
                    Consumed consumed = consumers.get(contract.name()).apply(body);
                    SpecificRecord event = consumed.event();
                    UUID eventId = (UUID)
                            event.get(event.getSchema().getField("eventId").pos());
                    send(contract.outputMessage().sentTo(), consumed.key(), eventId, consumed.event());
                    send(contract.outputMessage().sentTo(), consumed.key(), eventId, consumed.event());
                    Awaitility.await().atMost(WAIT).until(() -> journaled(eventId));
                    consumed.check().run();
                }));
    }

    record Consumed(String key, SpecificRecord event, ThrowingRunnable check) {}

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private Consumed timeTrackedContract(Map<String, Object> body) {
        UUID learner = UUID.randomUUID();
        TimeTrackedV1 event = TimeTrackedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(learner)
                .setAttemptId(UUID.randomUUID())
                .setTaskType(TaskType.valueOf((String) body.get("taskType")))
                .setSection(Section.valueOf((String) body.get("section")))
                .setWeekSection((String) body.get("weekSection"))
                .setLocalDate(today)
                .setActiveSeconds(95)
                .setItemCount((Integer) body.get("itemCount"))
                .build();
        return new Consumed(
                learner.toString(),
                event,
                () -> assertThat(jdbc.sql("SELECT active_seconds FROM activity_day WHERE user_id = :user")
                                .param("user", learner)
                                .query(Integer.class)
                                .single())
                        .isEqualTo(95));
    }

    private Consumed attemptSubmittedContract(Map<String, Object> body) {
        UUID learner = UUID.randomUUID();
        Instant at = Instant.now();
        AttemptSubmittedV1 event = AttemptSubmittedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(at)
                .setUserId(learner)
                .setAttemptId(UUID.randomUUID())
                .setItemSetId(UUID.randomUUID())
                .setTaskType(TaskType.valueOf((String) body.get("taskType")))
                .setSection(Section.READING)
                .setMode(AttemptMode.valueOf((String) body.get("mode")))
                .setStartedAt(at.minusSeconds(100))
                .setSubmittedAt(at)
                .setActiveSeconds(100)
                .setAnsweredCount((Integer) body.get("answeredCount"))
                .setTotalCount((Integer) body.get("totalCount"))
                .setAutoSubmitted((Boolean) body.get("autoSubmitted"))
                .build();
        int before = submittedOn(at);
        return new Consumed(
                learner.toString(), event, () -> assertThat(submittedOn(at)).isEqualTo(before + 1));
    }

    private Consumed weekTaskCompletedContract(Map<String, Object> body) {
        UUID learner = UUID.randomUUID();
        UUID weekId = UUID.randomUUID();
        WeekTaskCompletedV1 event = WeekTaskCompletedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(learner)
                .setWeekId(weekId)
                .setWeekTaskId(UUID.randomUUID())
                .setWeekNo((Integer) body.get("weekNo"))
                .setKind(WeekTaskKind.valueOf((String) body.get("kind")))
                .setTaskType(null)
                .setWeekSection(WeekSection.valueOf((String) body.get("weekSection")))
                .setEstimatedMinutes(10)
                .setLocalDate(today)
                .setAttemptId(null)
                .setProgressPercent(5.6)
                .build();
        return new Consumed(learner.toString(), event, () -> {
            assertThat(jdbc.sql("SELECT tasks_done FROM activity_day WHERE user_id = :user")
                            .param("user", learner)
                            .query(Integer.class)
                            .single())
                    .isEqualTo(1);
            assertThat(jdbc.sql("SELECT tasks_done FROM week_summary WHERE week_id = :week")
                            .param("week", weekId)
                            .query(Integer.class)
                            .single())
                    .isEqualTo(1);
        });
    }

    private Consumed usageRecordedContract(Map<String, Object> body) {
        String model = "contract-" + UUID.randomUUID();
        UsageRecordedV1 event = UsageRecordedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(null)
                .setAiCallId("aic_01JD3Q7M8Z5W6X4Y2V0T9S8R7Q")
                .setCallerService((String) body.get("callerService"))
                .setUseCase((String) body.get("useCase"))
                .setProvider(AiProvider.valueOf((String) body.get("provider")))
                .setModel(model)
                .setPromptKey((String) body.get("promptKey"))
                .setPromptVersion((Integer) body.get("promptVersion"))
                .setInputTokens(120)
                .setOutputTokens(30)
                .setAudioSeconds(0)
                .setCharacters(0)
                .setShadowCostMicroUsd(75)
                .setCacheHit((Boolean) body.get("cacheHit"))
                .setFallbackDepth((Integer) body.get("fallbackDepth"))
                .setDegraded((Boolean) body.get("degraded"))
                .setLatencyMs(300)
                .setOutcome(AiCallOutcome.valueOf((String) body.get("outcome")))
                .build();
        return new Consumed(
                event.getAiCallId(),
                event,
                () -> assertThat(jdbc.sql(
                                        "SELECT calls || '/' || cost_micro_usd FROM ai_usage_daily WHERE model = :model")
                                .param("model", model)
                                .query(String.class)
                                .single())
                        .isEqualTo("1/75"));
    }

    // ---- the learner journey ----

    /** Onboarding → plan → week → a slow attempt that meets the goal → a done task → estimate, θ and results. */
    private void journey(UUID learner) throws Exception {
        UUID attempt = UUID.randomUUID();
        Instant now = Instant.now();
        List<SpecificRecord> events = List.of(
                ProfileCreatedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(3600))
                        .setUserId(learner)
                        .setRoles(List.of(Role.STUDENT))
                        .setEmailVerified(true)
                        .build(),
                OnboardingCompletedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(3500))
                        .setUserId(learner)
                        .setRevision(1)
                        .setOverallTarget(5.0)
                        .setWritingTarget(5.5)
                        .setExamBooked(true)
                        .setExamDate(today.plusDays(40))
                        .setTimezone("Europe/Minsk")
                        .setExplanationLanguage("en")
                        .setMinutesPerDay(20)
                        .setPreferredStudyTime("19:00")
                        .build(),
                StudyPlanCreatedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(3400))
                        .setUserId(learner)
                        .setPlanId(UUID.randomUUID())
                        .setWeeksTotal(6)
                        .setStartDate(day1)
                        .setExamDate(today.plusDays(40))
                        .setMinutesPerDay(20)
                        .setTimezone("Europe/Minsk")
                        .setMockExamWeeks(List.of(4))
                        .build(),
                WeekPublishedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(3300))
                        .setUserId(learner)
                        .setWeekId(learnerWeek(learner))
                        .setPlanId(UUID.randomUUID())
                        .setWeekNo(1)
                        .setDay1(day1)
                        .setAutoPublished(false)
                        .setLockedByEntitlement(false)
                        .setTasks(List.of(
                                publishedTask(learnerTask(learner, 1), today),
                                publishedTask(learnerTask(learner, 2), today.plusDays(1))))
                        .build(),
                AttemptStartedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(3000))
                        .setUserId(learner)
                        .setAttemptId(attempt)
                        .setItemSetId(UUID.randomUUID())
                        .setTaskType(TaskType.READ_ACADEMIC_PASSAGE)
                        .setSection(Section.READING)
                        .setMode(AttemptMode.PRACTICE)
                        .setWeekTaskId(learnerTask(learner, 1))
                        .setItemSetVersion(1)
                        .setStartedAt(now.minusSeconds(3000))
                        .setItemCount(5)
                        .setEstimatedSeconds(1034)
                        .setRetry(false)
                        .build(),
                TimeTrackedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(1400))
                        .setUserId(learner)
                        .setAttemptId(attempt)
                        .setTaskType(TaskType.READ_ACADEMIC_PASSAGE)
                        .setSection(Section.READING)
                        .setWeekSection("READING")
                        .setLocalDate(today)
                        .setActiveSeconds(1500)
                        .setItemCount(5)
                        .build(),
                AttemptScoredV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(1300))
                        .setUserId(learner)
                        .setAttemptId(attempt)
                        .setItemSetId(UUID.randomUUID())
                        .setTaskType(TaskType.READ_ACADEMIC_PASSAGE)
                        .setSection(Section.READING)
                        .setMode(AttemptMode.PRACTICE)
                        .setWeekTaskId(learnerTask(learner, 1))
                        .setScoredAt(now.minusSeconds(1300))
                        .setCorrectCount(4)
                        .setTotalCount(5)
                        .setUnansweredCount(0)
                        .setActiveSeconds(1500)
                        .setEstimatedSeconds(1034)
                        .setTimingMultiplier(1.0)
                        .setThetaBefore(0.0)
                        .setUnaided(true)
                        .setItems(List.of())
                        .build(),
                WeekTaskCompletedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(1200))
                        .setUserId(learner)
                        .setWeekId(learnerWeek(learner))
                        .setWeekTaskId(learnerTask(learner, 1))
                        .setWeekNo(1)
                        .setKind(WeekTaskKind.ITEM_SET)
                        .setTaskType(TaskType.READ_ACADEMIC_PASSAGE)
                        .setWeekSection(WeekSection.READING)
                        .setEstimatedMinutes(17)
                        .setLocalDate(today)
                        .setAttemptId(attempt)
                        .setProgressPercent(50)
                        .build(),
                AbilityUpdatedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(1100))
                        .setUserId(learner)
                        .setSection(Section.READING)
                        .setAttemptId(attempt)
                        .setThetaBefore(0.0)
                        .setThetaAfter(0.42)
                        .setStandardError(0.35)
                        .setN(25)
                        .build(),
                BandEstimateUpdatedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(1000))
                        .setUserId(learner)
                        .setSections(List.of(SectionEstimateV1.newBuilder()
                                .setSection(Section.READING)
                                .setStatus(EstimateStatus.READY)
                                .setPointBand(4.5)
                                .setLow(4.0)
                                .setHigh(5.0)
                                .setN(25)
                                .build()))
                        .setOverallPoint(4.5)
                        .setOverallLow(4.0)
                        .setOverallHigh(5.0)
                        .setAsOf(now.minusSeconds(1000))
                        .build(),
                SubmissionEvaluatedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(900))
                        .setUserId(learner)
                        .setSubmissionId(UUID.randomUUID())
                        .setVersionNo(1)
                        .setEvaluationId(UUID.randomUUID())
                        .setItemSetId(UUID.randomUUID())
                        .setTaskType(TaskType.WRITE_EMAIL)
                        .setSection(Section.WRITING)
                        .setStatus(SubmissionStatus.EVALUATED)
                        .setEvaluatedAt(now.minusSeconds(900))
                        .setRubricScore(4)
                        .setPromptKey("writing.grade.email")
                        .setPromptVersion(1)
                        .setFinalScore(4.0)
                        .build(),
                UsageRecordedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(now.minusSeconds(900))
                        .setUserId(learner)
                        .setAiCallId("aic_01JD3Q7M8Z5W6X4Y2V0T9S8R7Q")
                        .setCallerService("assessment-service")
                        .setUseCase("WRITING_GRADING")
                        .setProvider(AiProvider.GROQ)
                        .setModel("openai/gpt-oss-120b")
                        .setPromptKey("writing.grade.email")
                        .setPromptVersion(1)
                        .setInputTokens(1800)
                        .setOutputTokens(400)
                        .setAudioSeconds(0)
                        .setCharacters(0)
                        .setShadowCostMicroUsd(900)
                        .setCacheHit(false)
                        .setFallbackDepth(0)
                        .setDegraded(false)
                        .setLatencyMs(2100)
                        .setOutcome(AiCallOutcome.SUCCESS)
                        .build());
        for (SpecificRecord event : events) {
            UUID eventId =
                    (UUID) event.get(event.getSchema().getField("eventId").pos());
            send(topic(event), learner.toString(), eventId, event);
            Awaitility.await().atMost(WAIT).until(() -> journaled(eventId));
        }
    }

    /** A closed earlier week: feeds week completion of the product metrics. */
    private void closeEarlierWeek(UUID learner) throws Exception {
        Instant now = Instant.now();
        WeekCompletedV1 event = WeekCompletedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(now)
                .setUserId(learner)
                .setWeekId(UUID.randomUUID())
                .setPlanId(UUID.randomUUID())
                .setWeekNo(1)
                .setStatus(WeekStatus.COMPLETED)
                .setStartDate(day1)
                .setEndDate(today)
                .setClosedAt(now)
                .setDayCount(3)
                .setExtended(false)
                .setFinishedEarly(true)
                .setClosedBy(WeekCloseReason.PROGRESS_70)
                .setProgressPercent(80)
                .setTasksTotal(10)
                .setTasksDone(8)
                .setTasksSkipped(0)
                .setMockExamWeek(false)
                .setSections(List.of())
                .build();
        send("toefly.planning.week-completed.v1", event);
        Awaitility.await().atMost(WAIT).until(() -> journaled(event.getEventId()));
    }

    private UUID learnerWeek(UUID learner) {
        return learner.equals(user)
                ? week
                : UUID.nameUUIDFromBytes(("week/" + learner).getBytes(StandardCharsets.UTF_8));
    }

    private UUID learnerTask(UUID learner, int no) {
        if (learner.equals(user)) {
            return no == 1 ? task1 : task2;
        }
        return UUID.nameUUIDFromBytes(("task/" + no + "/" + learner).getBytes(StandardCharsets.UTF_8));
    }

    private static PublishedWeekTaskV1 publishedTask(UUID taskId, LocalDate day) {
        return PublishedWeekTaskV1.newBuilder()
                .setWeekTaskId(taskId)
                .setKind(WeekTaskKind.ITEM_SET)
                .setWeekSection(WeekSection.READING)
                .setTaskType(TaskType.READ_ACADEMIC_PASSAGE)
                .setRefId(UUID.randomUUID())
                .setLocalDate(day)
                .setEstimatedMinutes(17)
                .setIsRepeat(false)
                .build();
    }

    private static String topic(SpecificRecord event) {
        return switch (event) {
            case ProfileCreatedV1 e -> "toefly.profile.profile-created.v1";
            case OnboardingCompletedV1 e -> "toefly.profile.onboarding-completed.v1";
            case StudyPlanCreatedV1 e -> "toefly.planning.study-plan-created.v1";
            case WeekPublishedV1 e -> "toefly.planning.week-published.v1";
            case WeekTaskCompletedV1 e -> "toefly.planning.week-task-completed.v1";
            case WeekCompletedV1 e -> "toefly.planning.week-completed.v1";
            case AttemptStartedV1 e -> "toefly.practice.attempt-started.v1";
            case AttemptScoredV1 e -> "toefly.practice.attempt-scored.v1";
            case TimeTrackedV1 e -> "toefly.practice.time-tracked.v1";
            case AbilityUpdatedV1 e -> "toefly.practice.ability-updated.v1";
            case BandEstimateUpdatedV1 e -> "toefly.assessment.band-estimate-updated.v1";
            case SubmissionEvaluatedV1 e -> "toefly.assessment.submission-evaluated.v1";
            case UsageRecordedV1 e -> "toefly.ai-gateway.usage-recorded.v1";
            default -> throw new IllegalArgumentException(event.getClass().getName());
        };
    }

    // ---- helpers ----

    private void send(String topic, SpecificRecord event) throws Exception {
        UUID eventId = (UUID) event.get(event.getSchema().getField("eventId").pos());
        UUID userId = (UUID) event.get(event.getSchema().getField("userId").pos());
        send(topic, userId.toString(), eventId, event);
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

    private boolean journaled(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM event_journal WHERE event_id = :id")
                        .param("id", eventId)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    private int count(String table, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = :user")
                .param("user", userId)
                .query(Integer.class)
                .single();
    }

    private int submittedOn(Instant at) {
        return jdbc.sql("SELECT coalesce(sum(attempts_submitted), 0) FROM product_metrics_daily WHERE day = :day")
                .param("day", java.sql.Date.valueOf(LocalDate.ofInstant(at, java.time.ZoneOffset.UTC)))
                .query(Integer.class)
                .single();
    }

    private List<List<String>> snapshot() {
        return READ_MODELS.stream()
                .map(sql -> jdbc.sql(sql).query(String.class).list())
                .toList();
    }

    @SuppressWarnings("unused")
    private static JsonNode json(String body) {
        return JSON.readTree(body);
    }
}
