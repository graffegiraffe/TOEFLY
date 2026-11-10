package com.toefly.practice.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.toefly.events.content.ItemSetPublishedV1;
import com.toefly.events.content.PublishedItem;
import com.toefly.practice.application.port.in.RebuildProjectionUseCase;
import com.toefly.practice.application.port.in.SyncItemPoolUseCase;
import com.toefly.practice.application.port.out.AttemptStorePort;
import com.toefly.practice.application.port.out.ConcurrencyConflictException;
import com.toefly.practice.application.port.out.ContentItemSetPort;
import com.toefly.practice.application.port.out.PracticeEventPublisherPort;
import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.Attempt;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Stream;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** practice-service against PostgreSQL, Kafka and Apicurio; content-service answers from fixtures. */
@SpringBootTest(properties = "toefly.practice.stale-attempts.enabled=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import({
    PostgresTestcontainersConfiguration.class,
    KafkaTestcontainersConfiguration.class,
    ApicurioTestcontainersConfiguration.class
})
class PracticeComponentIT {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Path CONTRACTS = Path.of("src/test/resources/contracts");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    SyncItemPoolUseCase pool;

    @Autowired
    AttemptStorePort store;

    @Autowired
    RebuildProjectionUseCase rebuild;

    @Autowired
    PracticeEventPublisherPort publisher;

    @Autowired
    EventPayloadSerializer serializer;

    @Autowired
    KafkaContainer kafka;

    @Value("${toefly.events.registry-url}")
    String registryUrl;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    ContentItemSetPort content;

    @BeforeEach
    void content() {
        when(content.fetch(any())).thenAnswer(call -> Fixtures.json(call.getArgument(0)));
        for (String type : Fixtures.TYPES) {
            pool.published(Fixtures.id(type), 1, Fixtures.examFormat(type), Fixtures.poolItems(type));
        }
    }

    // ---- every closed type ----

    @TestFactory
    @DisplayName("phase 3 DoD: every closed type is answered, scored and reviewed")
    Stream<DynamicTest> everyClosedType() {
        return Fixtures.TYPES.stream()
                .map(type -> DynamicTest.dynamicTest(type, () -> {
                    UUID user = UUID.randomUUID();
                    JsonNode attempt = start(user, type);
                    assertThat(attempt.path("status").asString()).isEqualTo("IN_PROGRESS");
                    assertThat(attempt.toString()).doesNotContain("answerKey").doesNotContain("rationale");
                    answerAll(user, attempt, Fixtures.answers(type));

                    JsonNode review =
                            body(submit(user, attempt.path("attemptId").asString(), UUID.randomUUID())
                                    .andExpect(status().isOk())
                                    .andReturn());

                    int total = attempt.path("items").size();
                    // a transformation that matches an accepted answer is right; the others are exact keys
                    assertThat(review.path("score").path("correct").asInt()).isEqualTo(total);
                    assertThat(review.path("score").path("total").asInt()).isEqualTo(total);
                    for (JsonNode item : review.path("items")) {
                        assertThat(item.path("correctAnswer").path("type").asString())
                                .isNotBlank();
                    }
                }));
    }

    @Test
    @DisplayName("AC-06.01.1, AC-06.01.6: start publishes attempt-started; submission publishes the rest in one go")
    void events() throws Exception {
        UUID user = UUID.randomUUID();
        int started = OutboxAssertions.published(jdbc, "toefly.practice.attempt-started.v1")
                .size();
        JsonNode attempt = start(user, "READ_ACADEMIC_PASSAGE");
        assertThat(OutboxAssertions.published(jdbc, "toefly.practice.attempt-started.v1"))
                .hasSize(started + 1);

        answerAll(user, attempt, Fixtures.answers("READ_ACADEMIC_PASSAGE"));
        JsonNode review = body(submit(user, id(attempt), UUID.randomUUID()).andReturn());

        assertThat(review.path("abilityChange").path("section").asString()).isEqualTo("READING");
        assertThat(review.path("abilityChange").path("thetaAfter").asDouble())
                .isGreaterThan(review.path("abilityChange").path("thetaBefore").asDouble());
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE partition_key = :user")
                        .param("user", user.toString())
                        .query(Integer.class)
                        .single())
                .isEqualTo(5);
        mvc.perform(get("/api/v1/abilities").with(student(user)))
                .andExpect(jsonPath("$.items[0].section").value("READING"))
                .andExpect(jsonPath("$.items[0].itemsObserved").value(2));
    }

    @Test
    @DisplayName("INV-ATT-01: a second start on the same set returns the open attempt")
    void oneOpenAttemptPerSet() throws Exception {
        UUID user = UUID.randomUUID();
        String first = id(start(user, "READ_DAILY_LIFE"));
        String second = id(start(user, "READ_DAILY_LIFE"));
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("AC-11.01.1: the review explains the right answer and every wrong option")
    void review() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode attempt = start(user, "READ_DAILY_LIFE");
        answer(user, attempt, 0, 1, "{\"type\":\"CHOICE\",\"optionIds\":[\"A\"]}");
        submit(user, id(attempt), UUID.randomUUID()).andExpect(status().isOk());

        mvc.perform(get("/api/v1/attempts/" + id(attempt) + "/review").with(student(user)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.score.correct").value(0))
                .andExpect(jsonPath("$.score.unanswered").value(1))
                .andExpect(jsonPath("$.items[0].userAnswer.optionIds[0]").value("A"))
                .andExpect(jsonPath("$.items[0].correctAnswer.optionIds[0]").value("B"))
                .andExpect(jsonPath("$.items[0].explanation.text").value("The text supports option B."))
                .andExpect(
                        jsonPath("$.items[0].distractorExplanations.length()").value(3))
                .andExpect(jsonPath("$.explanationPending").value(false));
    }

    // ---- concurrency and versions ----

    @Test
    @DisplayName("phase 3 DoD: concurrent writes to one stream give one success and one 409")
    void concurrentSubmissions() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode attempt = start(user, "LISTEN_CONVERSATION");
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<CompletableFuture<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    barrier.await();
                    return submit(user, id(attempt), UUID.randomUUID())
                            .andReturn()
                            .getResponse()
                            .getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        List<Integer> statuses =
                calls.stream().map(CompletableFuture::join).sorted().toList();
        assertThat(statuses).containsExactly(200, 409);
    }

    @Test
    @DisplayName("INV-ATT-03: two writers appending to the same version of a stream: the second one conflicts")
    void storeConflict() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode started = start(user, "LISTEN_CONVERSATION");
        UUID attemptId = UUID.fromString(id(started));
        UUID item = UUID.fromString(started.path("items").get(0).path("itemId").asString());
        Attempt first = store.load(attemptId).orElseThrow();
        Attempt second = store.load(attemptId).orElseThrow();
        first.recordAnswer(item, new Answer.Choice(List.of("A")), Instant.now(), null, null);
        second.recordAnswer(item, new Answer.Choice(List.of("B")), Instant.now(), null, null);
        var metadata = new AttemptStorePort.EventMetadata(user, UUID.randomUUID(), "");

        store.append(first, metadata);

        assertThatThrownBy(() -> store.append(second, metadata)).isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    @DisplayName("docs/05 §2.7: a stale If-Match is 412 with the current ETag, a missing one is 428")
    void versions() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode attempt = start(user, "READ_DAILY_LIFE");
        answer(user, attempt, 0, 1, "{\"type\":\"CHOICE\",\"optionIds\":[\"A\"]}");
        String url = "/api/v1/attempts/" + id(attempt) + "/answers/" + itemId(attempt, 0);

        mvc.perform(put(url).with(student(user))
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":{\"type\":\"CHOICE\",\"optionIds\":[\"B\"]}}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.code").value("VERSION_MISMATCH"))
                .andExpect(jsonPath("$.currentVersion").value(2));
        mvc.perform(put(url).with(student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":{\"type\":\"CHOICE\",\"optionIds\":[\"B\"]}}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
        mvc.perform(delete(url).with(student(user)).header("If-Match", "\"2\""))
                .andExpect(status().isNoContent())
                .andExpect(header().string("ETag", "\"3\""));
        mvc.perform(get("/api/v1/attempts/" + id(attempt)).with(student(user)).header("If-None-Match", "\"3\""))
                .andExpect(status().isNotModified());
    }

    // ---- idempotency ----

    @Test
    @DisplayName("AC-06.03.4: a repeated submission with the same Idempotency-Key replays the review")
    void idempotency() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode attempt = start(user, "READ_DAILY_LIFE");
        UUID key = UUID.randomUUID();
        String first = submit(user, id(attempt), key).andReturn().getResponse().getContentAsString();

        MvcResult replay = submit(user, id(attempt), key)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();
        assertThat(JSON.readTree(replay.getResponse().getContentAsString())).isEqualTo(JSON.readTree(first));
        assertThat(OutboxAssertions.published(jdbc, "toefly.practice.attempt-scored.v1").stream()
                        .filter(event -> event.partitionKey().equals(user.toString())))
                .hasSize(1);

        mvc.perform(post("/api/v1/attempts/" + id(attempt) + "/submission")
                        .with(student(user))
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"TIMEOUT\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSE"));
        mvc.perform(post("/api/v1/attempts")
                        .with(student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    // ---- security ----

    @Test
    @DisplayName("NFR-SEC-02: no token 401, a tutor cannot answer, a foreign attempt looks missing")
    void security() throws Exception {
        UUID owner = UUID.randomUUID();
        JsonNode attempt = start(owner, "READ_DAILY_LIFE");

        mvc.perform(get("/api/v1/attempts/" + id(attempt))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/attempts/" + id(attempt)).with(student(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/attempts/" + id(attempt)).with(JwtFixtures.tutor(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/attempts/" + id(attempt) + "/submission")
                        .with(JwtFixtures.tutor(owner))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/v1/attempts").with(student(owner))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("INV-ATT-06: a hidden set accepts no new attempts; running attempts continue")
    void hiddenSet() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode running = start(user, "VOCAB_EXERCISE");
        pool.hidden(Fixtures.id("VOCAB_EXERCISE"));

        startRequest(UUID.randomUUID(), "VOCAB_EXERCISE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_SET_UNAVAILABLE"));
        submit(user, id(running), UUID.randomUUID()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-06.01.3, INV-ATT-07: Practice pauses through heartbeats; an EXAM attempt cannot pause")
    void pauses() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode attempt = start(user, "READ_ACADEMIC_PASSAGE");
        heartbeat(user, id(attempt), 1, "PAUSED").andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/attempts/" + id(attempt)).with(student(user)))
                .andExpect(jsonPath("$.paused").value(true));
        heartbeat(user, id(attempt), 2, "RESUMED").andExpect(status().isNoContent());
        heartbeat(user, id(attempt), 3, "ACTIVE").andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/attempts/" + id(attempt)).with(student(user)))
                .andExpect(jsonPath("$.paused").value(false))
                .andExpect(jsonPath("$.version").value(3));

        JsonNode exam = body(mvc.perform(post("/internal/v1/attempts")
                        .with(service())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "userId", user.toString(),
                                "itemSetId", Fixtures.id("LISTEN_ANNOUNCEMENT").toString(),
                                "examSessionId", UUID.randomUUID().toString(),
                                "moduleId", UUID.randomUUID().toString(),
                                "deadlineAt",
                                        Instant.now()
                                                .plus(Duration.ofMinutes(10))
                                                .toString()))))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(exam.path("mode").asString()).isEqualTo("EXAM");
        heartbeat(user, id(exam), 1, "PAUSED").andExpect(status().isConflict());
    }

    // ---- projections and GDPR ----

    @Test
    @DisplayName("ADR-0010: rebuilding attempt_view from the event store gives the inline projection")
    void rebuildEqualsInline() throws Exception {
        UUID user = UUID.randomUUID();
        for (String type : List.of("READ_DAILY_LIFE", "BUILD_A_SENTENCE", "GRAMMAR_EXERCISE")) {
            JsonNode attempt = start(user, type);
            answer(user, attempt, 0, 1, Fixtures.answers(type).getFirst());
            if (!type.equals("GRAMMAR_EXERCISE")) {
                submit(user, id(attempt), UUID.randomUUID()).andExpect(status().isOk());
            }
        }
        String query = """
                SELECT attempt_id, status, item_set_version, raw_score, correct_count, unanswered_count, answers::text,
                    submitted_at, scored_at, version
                FROM attempt_view ORDER BY attempt_id
                """;
        List<Map<String, Object>> inline = jdbc.sql(query).query().listOfRows();

        rebuild.rebuild();

        assertThat(jdbc.sql(query).query().listOfRows()).isEqualTo(inline);
    }

    @Test
    @DisplayName("docs/03 §14: erasure deletes the streams and projections of the user")
    void erasure() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode attempt = start(user, "READ_DAILY_LIFE");
        submit(user, id(attempt), UUID.randomUUID()).andExpect(status().isOk());

        mvc.perform(get("/internal/v1/users/" + user + "/export").with(service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(1));

        sendAccountDeletion(user);
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(jdbc.sql("SELECT count(*) FROM attempt_event WHERE user_id = :user")
                                .param("user", user)
                                .query(Integer.class)
                                .single())
                        .isZero());
        assertThat(store.load(UUID.fromString(id(attempt)))).isEmpty();
    }

    @Test
    @DisplayName("contract item_set_published: a published set becomes available with its item ids and difficulty")
    void itemSetPublishedFromKafka() throws Exception {
        UUID set = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        ItemSetPublishedV1 event = ItemSetPublishedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setItemSetId(set)
                .setVersion(1)
                .setTaskType("READ_DAILY_LIFE")
                .setSection("READING")
                .setExamFormat("TOEFL_2026")
                .setCefr("B1")
                .setDifficultyB(-0.5)
                .setItemCount(1)
                .setEstimatedMinutes(1.0)
                .setTopic("campus library")
                .setSkills(List.of("reading.detail"))
                .setItems(List.of(PublishedItem.newBuilder()
                        .setItemId(item)
                        .setOrdinal(1)
                        .setB(-0.5)
                        .build()))
                .build();
        send("toefly.content.item-set-published.v1", set.toString(), event.getEventId(), event);
        send("toefly.content.item-set-published.v1", set.toString(), event.getEventId(), event);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(jdbc.sql("SELECT b FROM item_difficulty WHERE item_id = :item")
                                .param("item", item)
                                .query(Double.class)
                                .single())
                        .isEqualTo(-0.5));
        assertThat(jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                        .param("id", event.getEventId())
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    // ---- contracts ----

    @TestFactory
    @DisplayName("ADR-0031: practice events fulfil the contracts of their consumers")
    Stream<DynamicTest> eventContracts() {
        OutboxMessageReceiver receiver = new OutboxMessageReceiver(jdbc, registryUrl);
        return ContractTests.messages(CONTRACTS, this, receiver).onClose(receiver::close);
    }

    @TestFactory
    @DisplayName("ADR-0031: the internal API fulfils the contracts of mockexam, tutor-agent and profile")
    Stream<DynamicTest> restContracts() {
        UUID owner = UUID.fromString(ContractData.STUDENT);
        prepareContractData(owner);
        return Stream.of("mockexam-service", "tutor-agent-service", "profile-service")
                .flatMap(caller -> ContractTests.rest(CONTRACTS.resolve(caller), MockMvcExchanger.of(mvc, service())));
    }

    @Test
    @DisplayName("docs/11 §8.5: every practice topic has a message contract")
    void contractsCoverTopics() {
        ContractCoverage.assertMessagesCover(CONTRACTS, publisher.topics());
    }

    void attemptStarted() throws Exception {
        start(UUID.randomUUID(), "READ_DAILY_LIFE");
    }

    void attemptSubmitted() throws Exception {
        UUID user = UUID.randomUUID();
        submit(user, id(start(user, "READ_DAILY_LIFE")), UUID.randomUUID());
    }

    void attemptScored() throws Exception {
        attemptSubmitted();
    }

    void abilityUpdated() throws Exception {
        attemptSubmitted();
    }

    void timeTracked() throws Exception {
        attemptSubmitted();
    }

    /** A scored attempt and an open EXAM-ready set for the fixed ids of the REST contracts. */
    private void prepareContractData(UUID owner) {
        try {
            if (jdbc.sql("SELECT count(*) FROM attempt_view WHERE user_id = :user")
                            .param("user", owner)
                            .query(Integer.class)
                            .single()
                    == 0) {
                JsonNode attempt = start(owner, "READ_DAILY_LIFE");
                submit(owner, id(attempt), UUID.randomUUID()).andExpect(status().isOk());
                ContractData.scoredAttempt = id(attempt);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- helpers ----

    private JsonNode start(UUID user, String type) throws Exception {
        return body(startRequest(user, type).andExpect(status().isCreated()).andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions startRequest(UUID user, String type) throws Exception {
        return mvc.perform(post("/api/v1/attempts")
                .with(student(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemSetId\":\"" + Fixtures.id(type) + "\",\"timeZone\":\"Europe/Minsk\"}"));
    }

    private void answerAll(UUID user, JsonNode attempt, List<String> answers) throws Exception {
        int version = attempt.path("version").asInt();
        for (int i = 0; i < answers.size(); i++) {
            version = answer(user, attempt, i, version, answers.get(i));
        }
    }

    private int answer(UUID user, JsonNode attempt, int index, int version, String answer) throws Exception {
        MvcResult result = mvc.perform(put("/api/v1/attempts/" + id(attempt) + "/answers/" + itemId(attempt, index))
                        .with(student(user))
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":" + answer + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).path("attemptVersion").asInt();
    }

    private org.springframework.test.web.servlet.ResultActions submit(UUID user, String attemptId, UUID key)
            throws Exception {
        return mvc.perform(post("/api/v1/attempts/" + attemptId + "/submission")
                .with(student(user))
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private org.springframework.test.web.servlet.ResultActions heartbeat(
            UUID user, String attemptId, long sequence, String state) throws Exception {
        return mvc.perform(post("/api/v1/attempts/" + attemptId + "/heartbeats")
                .with(student(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sequence\":" + sequence + ",\"state\":\"" + state + "\",\"activeSecondsDelta\":0}"));
    }

    private void sendAccountDeletion(UUID user) throws Exception {
        var event = com.toefly.events.profile.AccountDeletionRequestedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setDeletionId(UUID.randomUUID())
                .setRequestedAt(Instant.now())
                .setLegalDeadlineAt(Instant.now().plus(Duration.ofDays(30)))
                .setRoles(List.of(com.toefly.events.common.Role.STUDENT))
                .build();
        send("toefly.profile.account-deletion-requested.v1", user.toString(), event.getEventId(), event);
    }

    private void send(String topic, String key, UUID eventId, org.apache.avro.specific.SpecificRecord event)
            throws Exception {
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

    private static String id(JsonNode attempt) {
        return attempt.path("attemptId").asString();
    }

    private static String itemId(JsonNode attempt, int index) {
        return attempt.path("items").get(index).path("itemId").asString();
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static JwtRequestPostProcessor student(UUID user) {
        return JwtFixtures.student(user);
    }

    private static JwtRequestPostProcessor service() {
        return JwtFixtures.withRoles(UUID.randomUUID(), "SERVICE");
    }

    /** Fixed identifiers used by the REST contracts. */
    static final class ContractData {
        static final String STUDENT = "0b6f1c0e-3b0c-4f3e-9d57-6a3f1d1d7a11";
        static String scoredAttempt = "";

        private ContractData() {}
    }
}
