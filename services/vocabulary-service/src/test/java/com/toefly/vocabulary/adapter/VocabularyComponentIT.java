package com.toefly.vocabulary.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.toefly.events.profile.AccountDeletionRequestedV1;
import com.toefly.events.profile.PreferencesUpdatedV1;
import com.toefly.vocabulary.application.port.in.EnrichmentUseCase;
import com.toefly.vocabulary.application.port.in.WordSetUseCase;
import com.toefly.vocabulary.application.port.out.VocabularyEventPublisherPort;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * vocabulary-service against PostgreSQL, Kafka and Apicurio; ai-gateway and content-service answer from WireMock
 * with recorded responses ({@code src/test/resources/ai-fixtures}, {@code content-fixtures}).
 */
@SpringBootTest(properties = {"toefly.vocabulary.jobs.enabled=false", "toefly.vocabulary.ai.enabled=true"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import({
    PostgresTestcontainersConfiguration.class,
    KafkaTestcontainersConfiguration.class,
    ApicurioTestcontainersConfiguration.class,
    VocabularyComponentIT.TestClock.class
})
class VocabularyComponentIT {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Path AI = Path.of("src/test/resources/ai-fixtures");
    static final Path CONTENT = Path.of("src/test/resources/content-fixtures");
    static final Path CONTRACTS = Path.of("src/test/resources/contracts");
    static final String COMPLETIONS = "/internal/v1/ai/chat/completions";
    static final String WORD_ADDED = "toefly.vocabulary.word-added.v1";
    static final String REVIEW_COMPLETED = "toefly.vocabulary.review-completed.v1";
    static final UUID ITEM_SET = UUID.fromString("4e2f6a1b-3c5d-4e7f-9a0b-1c2d3e4f5a6b");
    static final UUID UNIT_1 = UUID.fromString("7d3e1f20-1a2b-4c3d-8e4f-5a6b7c8d9e01");
    static final UUID CONTRACT_STUDENT = UUID.fromString("5a1c0e7d-2b3f-4c6d-8e9f-0a1b2c3d4e5f");
    static final WireMockServer DEPENDENCIES = new WireMockServer(options().dynamicPort());

    static {
        DEPENDENCIES.start();
    }

    @DynamicPropertySource
    static void dependencies(DynamicPropertyRegistry registry) {
        registry.add("toefly.vocabulary.content-url", DEPENDENCIES::baseUrl);
        registry.add("toefly.vocabulary.ai-gateway-url", DEPENDENCIES::baseUrl);
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
    KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    WordSetUseCase wordSets;

    @Autowired
    EnrichmentUseCase enrichment;

    @Autowired
    VocabularyEventPublisherPort publisher;

    @Value("${toefly.events.registry-url}")
    String registryUrl;

    @MockitoBean
    JwtDecoder jwtDecoder;

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
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS))
                .withRequestBody(matchingJsonPath("$.promptKey", WireMock.equalTo("vocabulary.word-card")))
                .withRequestBody(matchingJsonPath("$.useCase", WireMock.equalTo("WORD_CARD")))
                .withRequestBody(matchingJsonPath("$.variables.term", WireMock.matching("mitigat.*")))
                .willReturn(okJson(Files.readString(AI.resolve("word-card-mitigating.json")))));
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS))
                .withRequestBody(matchingJsonPath("$.promptKey", WireMock.equalTo("vocabulary.key-words")))
                .withRequestBody(matchingJsonPath("$.useCase", WireMock.equalTo("KEY_WORDS")))
                .willReturn(okJson(Files.readString(AI.resolve("key-words-urban-forests.json")))));
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS))
                .atPriority(10)
                .willReturn(aResponse().withStatus(422)));
        DEPENDENCIES.stubFor(WireMock.get(urlPathEqualTo("/api/v1/vocabulary-units"))
                .willReturn(okJson(Files.readString(CONTENT.resolve("vocabulary-units.json")))));
        DEPENDENCIES.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/item-sets/" + ITEM_SET))
                .willReturn(okJson(Files.readString(CONTENT.resolve("reading-item-set.json")))));
        DEPENDENCIES.stubFor(WireMock.get(urlPathMatching("/internal/v1/item-sets/.*"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(404)));
    }

    // ---- saving words ----

    @Test
    @DisplayName("AC-15.01.1, AC-15.01.5: the word card comes from ai-gateway once, then from the shared cache")
    void wordCard() throws Exception {
        jdbc.sql("DELETE FROM dictionary_cache WHERE lemma = 'mitigate'").update();
        UUID user = UUID.randomUUID();

        JsonNode card = body(mvc.perform(get("/api/v1/word-lookups")
                        .with(JwtFixtures.student(user))
                        .param("term", "Mitigating")
                        .param("context", "Urban forests mitigate the heat island effect."))
                .andExpect(status().isOk()));
        assertThat(card.path("lemma").asString()).isEqualTo("mitigate");
        assertThat(card.path("available").asBoolean()).isTrue();
        assertThat(card.path("data").path("translationRu").asString()).isEqualTo("смягчать, ослаблять");
        assertThat(card.path("data").path("ipa").asString()).isEqualTo("/ˈmɪt.ɪ.ɡeɪt/");
        assertThat(card.path("pronunciationAvailable").asBoolean()).isFalse();
        assertThat(card.path("entryId").isNull()).isTrue();

        mvc.perform(get("/api/v1/word-lookups")
                        .with(JwtFixtures.student(UUID.randomUUID()))
                        .param("term", "mitigate"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.definitionEn").value("to make something harmful or unpleasant less severe"));
        DEPENDENCIES.verify(1, postRequestedFor(urlPathEqualTo(COMPLETIONS)));

        mvc.perform(get("/api/v1/word-lookups").with(JwtFixtures.student(user)).param("term", "  1984 "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("AC-15.01.2: Save word creates the entry with its context, a NEW card and word-added")
    void saveWord() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode entry = body(addWord(user, "mitigate", "Urban forests mitigate the heat island effect.")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location")));

        assertThat(entry.path("lemma").asString()).isEqualTo("mitigate");
        assertThat(entry.path("status").asString()).isEqualTo("NEW");
        assertThat(entry.path("card").path("state").asString()).isEqualTo("NEW");
        assertThat(entry.path("alreadyInDictionary").asBoolean()).isFalse();
        assertThat(entry.path("contexts").get(0).path("itemSetId").asString()).isEqualTo(ITEM_SET.toString());
        assertThat(entry.path("contexts").get(0).path("sourceLabel").asString()).isEqualTo("Urban forests");
        assertThat(entry.path("data").path("definitionEn").asString()).isNotBlank();

        var event = publishedOnce(
                WORD_ADDED, "vocabulary-entries/" + entry.path("id").asString());
        assertThat(event.partitionKey()).isEqualTo(user.toString());
        assertThat(event.source()).isEqualTo("/toefly/vocabulary-service");
    }

    @Test
    @DisplayName("AC-15.01.3: saving the lemma again adds a context, no duplicate and no second event")
    void saveTwice() throws Exception {
        UUID user = UUID.randomUUID();
        String id = body(addWord(user, "mitigate", "Urban forests mitigate the heat island effect."))
                .path("id")
                .asString();
        JsonNode again =
                body(addWord(user, "Mitigate", "Wetlands mitigate floods.").andExpect(status().isOk()));

        assertThat(again.path("id").asString()).isEqualTo(id);
        assertThat(again.path("alreadyInDictionary").asBoolean()).isTrue();
        assertThat(again.path("contexts")).hasSize(2);
        assertThat(count("SELECT count(*) FROM vocabulary_entry WHERE user_id = :u", user))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM outbox_event WHERE topic = '" + WORD_ADDED + "' AND partition_key = :u",
                        user.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("docs/04 §3.12: without ai-gateway the word is saved bare and enriched by the background job")
    void enrichmentLater() throws Exception {
        jdbc.sql("DELETE FROM dictionary_cache WHERE lemma = 'mitigate'").update();
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS))
                .atPriority(1)
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "30")));
        UUID user = UUID.randomUUID();
        JsonNode bare =
                body(addWord(user, "mitigate", "Wetlands mitigate floods.").andExpect(status().isCreated()));
        assertThat(bare.path("enriched").asBoolean()).isFalse();
        assertThat(bare.path("data").path("definitionEn").isNull()).isTrue();

        assertThat(enrichment.enrichPending(50)).isZero();
        DEPENDENCIES.resetAll();
        stubs();
        assertThat(enrichment.enrichPending(50)).isGreaterThanOrEqualTo(1);

        mvc.perform(get("/api/v1/vocabulary-entries/" + bare.path("id").asString())
                        .with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enriched").value(true))
                .andExpect(jsonPath("$.data.translationRu").value("смягчать, ослаблять"));
    }

    @Test
    @DisplayName("docs/05 §2.6: a repeated Idempotency-Key replays the stored answer")
    void idempotency() throws Exception {
        UUID user = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String body = "{\"term\":\"canopy\",\"context\":\"A dense canopy shades the streets.\"}";
        mvc.perform(post("/api/v1/vocabulary-entries")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/vocabulary-entries")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        mvc.perform(post("/api/v1/vocabulary-entries")
                        .with(JwtFixtures.student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    // ---- reviews ----

    @Test
    @DisplayName("AC-15.03.1, AC-15.03.2, AC-15.03.3, AC-15.03.5: a session grades the day's cards and completes")
    void reviewSession() throws Exception {
        UUID user = UUID.randomUUID();
        for (String term : List.of("canopy", "evaporation", "habitat")) {
            addWord(user, term, null).andExpect(status().isCreated());
        }
        JsonNode due = body(mvc.perform(get("/api/v1/review-cards/due").with(JwtFixtures.student(user)))
                .andExpect(status().isOk()));
        assertThat(due.path("newCount").asInt()).isEqualTo(3);
        assertThat(due.path("dueCount").asInt()).isZero();
        assertThat(due.path("items")).hasSize(3);
        assertThat(due.path("items").get(0).path("intervals").path("again").asLong())
                .isEqualTo(60);
        assertThat(due.path("items").get(0).path("intervals").path("good").asLong())
                .isEqualTo(600);

        JsonNode session = startSession(user);
        String sessionId = session.path("id").asString();
        assertThat(session.path("planned").asInt()).isEqualTo(3);

        JsonNode cards = session.path("cards");
        String first = cards.get(0).path("id").asString();
        JsonNode again = body(grade(user, first, "AGAIN", sessionId, 0).andExpect(status().isOk()));
        assertThat(again.path("card").path("state").asString()).isEqualTo("LEARNING");
        assertThat(Duration.between(
                        Instant.now(),
                        Instant.parse(again.path("card").path("due").asString())))
                .isBetween(Duration.ZERO, Duration.ofMinutes(10));

        // the same grade sent twice is counted once (INV-VOC-04)
        grade(user, first, "AGAIN", sessionId, 0).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM review_log WHERE user_id = :u", user))
                .isEqualTo(1);

        grade(user, first, "GOOD", sessionId, 3).andExpect(status().isOk());
        grade(user, cards.get(1).path("id").asString(), "EASY", sessionId, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.state").value("REVIEW"));
        grade(user, cards.get(2).path("id").asString(), "GOOD", sessionId, 2).andExpect(status().isOk());

        JsonNode done = body(complete(user, sessionId).andExpect(status().isOk()));
        assertThat(done.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.path("reviewed").asInt()).isEqualTo(3);
        assertThat(done.path("againCount").asInt()).isEqualTo(1);
        assertThat(done.path("newIntroduced").asInt()).isEqualTo(3);
        assertThat(done.path("nextDueInSeconds").asLong()).isPositive();
        publishedOnce(REVIEW_COMPLETED, "review-sessions/" + sessionId);

        complete(user, sessionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_COMPLETED"));
        grade(user, first, "GOOD", sessionId, 9)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_COMPLETED"));

        // the new cards of the day are used up
        mvc.perform(get("/api/v1/review-cards/due").with(JwtFixtures.student(user)))
                .andExpect(jsonPath("$.newCount").value(0));
    }

    @Test
    @DisplayName("Phase 8 DoD: a reviewed card returns in the queue on its local day (Europe/Minsk)")
    void dueOnLocalDay() throws Exception {
        UUID user = UUID.randomUUID();
        send("toefly.profile.preferences-updated.v1", user, preferences(user, "Europe/Minsk"));
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .until(() -> count("SELECT count(*) FROM learner_settings WHERE user_id = :u", user) == 1);

        addWord(user, "allocate", "Cities allocate funds to planting.").andExpect(status().isCreated());
        JsonNode session = startSession(user);
        assertThat(session.path("timezone").asString()).isEqualTo("Europe/Minsk");
        String card = session.path("cards").get(0).path("id").asString();
        JsonNode easy = body(grade(user, card, "EASY", session.path("id").asString(), 0));
        Instant due = Instant.parse(easy.path("card").path("due").asString());
        assertThat(due.atZone(ZoneId.of("Europe/Minsk")).toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
        complete(user, session.path("id").asString()).andExpect(status().isOk());

        TestClock.CLOCK.offset = Duration.between(Instant.now(), due).minusMinutes(5);
        mvc.perform(get("/api/v1/review-cards/due").with(JwtFixtures.student(user)))
                .andExpect(jsonPath("$.dueCount").value(0));
        TestClock.CLOCK.offset = Duration.between(Instant.now(), due).plusMinutes(1);
        JsonNode next = body(mvc.perform(get("/api/v1/review-cards/due").with(JwtFixtures.student(user))));
        assertThat(next.path("dueCount").asInt()).isEqualTo(1);
        assertThat(next.path("localDate").asString())
                .isEqualTo(LocalDate.ofInstant(due, ZoneId.of("Europe/Minsk")).toString());
        assertThat(next.path("items").get(0).path("lemma").asString()).isEqualTo("allocate");
    }

    @Test
    @DisplayName("AC-15.03.1: a review card that is not due cannot be graded outside its session")
    void notDue() throws Exception {
        UUID user = UUID.randomUUID();
        addWord(user, "substantial", null).andExpect(status().isCreated());
        JsonNode first = startSession(user);
        String card = first.path("cards").get(0).path("id").asString();
        grade(user, card, "EASY", first.path("id").asString(), 0).andExpect(status().isOk());
        complete(user, first.path("id").asString()).andExpect(status().isOk());

        JsonNode second = startSession(user);
        assertThat(second.path("planned").asInt()).isZero();
        grade(user, card, "GOOD", second.path("id").asString(), 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CARD_NOT_DUE"));
    }

    // ---- word sets ----

    @Test
    @DisplayName("AC-15.04.2, AC-15.04.3: an approved Collins unit is added to the dictionary once, in unit order")
    void wordSet() throws Exception {
        // the job runs outside any HTTP request, as the scheduler does
        assertThat(java.util.concurrent.CompletableFuture.supplyAsync(wordSets::sync)
                        .get())
                .isEqualTo(1);
        UUID user = UUID.randomUUID();
        addWord(user, "mitigate", null).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/word-sets").with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].unitNo").value(1))
                .andExpect(jsonPath("$.items[0].wordsTotal").value(3))
                .andExpect(jsonPath("$.items[0].inDictionary").value(1))
                .andExpect(jsonPath("$.items[0].sourceLabel").value("Collins Vocabulary Unit 1"));
        mvc.perform(get("/api/v1/word-sets/" + UNIT_1).with(JwtFixtures.student(user)))
                .andExpect(jsonPath("$.words[0].lemma").value("habitat"))
                .andExpect(jsonPath("$.words[0].exampleEn").value("The wetland is a habitat for many birds."));

        enroll(user)
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.alreadyInDictionary").value(1));
        enroll(user)
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.alreadyInDictionary").value(3));

        JsonNode page = body(mvc.perform(get("/api/v1/vocabulary-entries")
                .with(JwtFixtures.student(user))
                .param("wordSetId", UNIT_1.toString())));
        assertThat(page.path("total").asInt()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("origin").asString()).isEqualTo("COLLINS_UNIT");
        assertThat(page.path("items").findValuesAsString("sourceLabel")).contains("Collins Vocabulary Unit 1");
        DEPENDENCIES.verify(
                0,
                postRequestedFor(urlPathEqualTo(COMPLETIONS))
                        .withRequestBody(matchingJsonPath("$.variables.term", WireMock.equalTo("habitat"))));
        mvc.perform(post("/api/v1/word-sets/" + UUID.randomUUID() + "/enrollment")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    // ---- key words ----

    @Test
    @DisplayName("AC-15.02.1, AC-15.02.2: key words of an ItemSet skip known words and are added with one action")
    void keyWords() throws Exception {
        jdbc.sql("DELETE FROM key_word_cache WHERE item_set_id = :id")
                .param("id", ITEM_SET)
                .update();
        UUID user = UUID.randomUUID();
        addWord(user, "mitigate", null).andExpect(status().isCreated());

        JsonNode words = body(mvc.perform(post("/api/v1/word-suggestions")
                        .with(JwtFixtures.student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemSetId\":\"" + ITEM_SET + "\"}"))
                .andExpect(status().isOk()));
        assertThat(words.path("items").findValuesAsString("lemma"))
                .containsExactly("canopy", "evaporation", "infrastructure", "allocate", "substantial");

        mvc.perform(post("/api/v1/word-suggestions")
                        .with(JwtFixtures.student(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemSetId\":\"" + ITEM_SET + "\"}"))
                .andExpect(jsonPath("$.items.length()").value(6));
        DEPENDENCIES.verify(
                1,
                postRequestedFor(urlPathEqualTo(COMPLETIONS))
                        .withRequestBody(matchingJsonPath("$.promptKey", WireMock.equalTo("vocabulary.key-words"))));

        JsonNode batch = body(mvc.perform(post("/api/v1/vocabulary-entry-batches")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"words": [{"term": "canopy", "context": "A dense canopy shades the streets."},
                                           {"term": "mitigate"}],
                                 "sourceRef": {"itemSetId": "%s", "label": "Urban forests"}}
                                """.formatted(ITEM_SET)))
                .andExpect(status().isOk()));
        assertThat(batch.path("added").asInt()).isEqualTo(1);
        assertThat(batch.path("alreadyInDictionary").asInt()).isEqualTo(1);
        assertThat(batch.path("items").get(0).path("origin").asString()).isEqualTo("KEY_WORDS");

        mvc.perform(post("/api/v1/word-suggestions")
                        .with(JwtFixtures.student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC-15.02.1: without ai-gateway the key words answer 503 ai-unavailable")
    void keyWordsUnavailable() throws Exception {
        DEPENDENCIES.stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS))
                .atPriority(1)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "60")));
        mvc.perform(post("/api/v1/word-suggestions")
                        .with(JwtFixtures.student(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Photosynthesis converts light into chemical energy.\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    // ---- My words ----

    @Test
    @DisplayName("AC-15.06.1: My words searches, filters by status and source, sorts and pages")
    void myWords() throws Exception {
        UUID user = UUID.randomUUID();
        for (String term : List.of("allocate", "allot", "canopy", "habitat", "substantial")) {
            addWord(user, term, null).andExpect(status().isCreated());
        }
        JsonNode search = body(mvc.perform(get("/api/v1/vocabulary-entries")
                .with(JwtFixtures.student(user))
                .param("q", "all")
                .param("sort", "LEMMA")));
        assertThat(search.path("items").findValuesAsString("lemma")).containsExactly("allocate", "allot");

        JsonNode first = body(mvc.perform(get("/api/v1/vocabulary-entries")
                .with(JwtFixtures.student(user))
                .param("limit", "2")
                .param("sort", "CREATED_ASC")));
        assertThat(first.path("total").asInt()).isEqualTo(5);
        assertThat(first.path("page").path("hasMore").asBoolean()).isTrue();
        JsonNode second = body(mvc.perform(get("/api/v1/vocabulary-entries")
                .with(JwtFixtures.student(user))
                .param("limit", "2")
                .param("sort", "CREATED_ASC")
                .param("cursor", first.path("page").path("nextCursor").asString())));
        assertThat(second.path("items").findValuesAsString("lemma")).containsExactly("canopy", "habitat");
        mvc.perform(get("/api/v1/vocabulary-entries")
                        .with(JwtFixtures.student(user))
                        .param("limit", "2")
                        .param("sort", "LEMMA")
                        .param("cursor", first.path("page").path("nextCursor").asString()))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/vocabulary-entries")
                        .with(JwtFixtures.student(user))
                        .param("status", "NEW"))
                .andExpect(jsonPath("$.total").value(5));
        mvc.perform(get("/api/v1/vocabulary-entries")
                        .with(JwtFixtures.student(user))
                        .param("status", "LEARNING"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/vocabulary-entries")
                        .with(JwtFixtures.student(user))
                        .param("origin", "TAP"))
                .andExpect(jsonPath("$.total").value(5));
        mvc.perform(get("/api/v1/vocabulary-entries")
                        .with(JwtFixtures.student(user))
                        .param("due", "true"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/vocabulary-entries").with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("AC-15.06.2: Suspend keeps the card out of the queue; Delete removes entry, card and log")
    void suspendAndDelete() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode entry = body(addWord(user, "evaporation", null));
        String id = entry.path("id").asString();

        String etag = mvc.perform(get("/api/v1/vocabulary-entries/" + id).with(JwtFixtures.student(user)))
                .andReturn()
                .getResponse()
                .getHeader("ETag");
        assertThat(etag).isEqualTo("\"0\"");
        mvc.perform(patch("/api/v1/vocabulary-entries/" + id)
                        .with(JwtFixtures.student(user))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suspended\":true,\"translationRu\":\"испарение\",\"note\":\"physics\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.card.suspended").value(true))
                .andExpect(jsonPath("$.data.translationRu").value("испарение"))
                .andExpect(jsonPath("$.note").value("physics"));
        mvc.perform(patch("/api/v1/vocabulary-entries/" + id)
                        .with(JwtFixtures.student(user))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suspended\":false}"))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(patch("/api/v1/vocabulary-entries/" + id)
                        .with(JwtFixtures.student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suspended\":false}"))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(get("/api/v1/review-cards/due").with(JwtFixtures.student(user)))
                .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(delete("/api/v1/vocabulary-entries/" + id).with(JwtFixtures.student(user)))
                .andExpect(status().isNoContent());
        assertThat(count("SELECT count(*) FROM review_card WHERE user_id = :u", user))
                .isZero();
        mvc.perform(get("/api/v1/vocabulary-entries/" + id).with(JwtFixtures.student(user)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/vocabulary-entries/" + id).with(JwtFixtures.student(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-15.06.3: Mastered lists cards with FSRS stability of 90 days or more")
    void mastered() throws Exception {
        UUID user = UUID.randomUUID();
        String id = body(addWord(user, "habitat", null)).path("id").asString();
        jdbc.sql("UPDATE review_card SET state = 'REVIEW', stability = 120, last_review_at = now(), "
                        + "due = now() + interval '100 days' WHERE entry_id = :id::uuid")
                .param("id", id)
                .update();
        mvc.perform(get("/api/v1/vocabulary-entries")
                        .with(JwtFixtures.student(user))
                        .param("status", "MASTERED"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].status").value("MASTERED"));
    }

    @Test
    @DisplayName("Settings: new cards per day stay within 5-30 (INV-VOC-05)")
    void settings() throws Exception {
        UUID user = UUID.randomUUID();
        mvc.perform(get("/api/v1/vocabulary-settings").with(JwtFixtures.student(user)))
                .andExpect(jsonPath("$.newCardsPerDay").value(10))
                .andExpect(jsonPath("$.timezone").value("UTC"));
        mvc.perform(put("/api/v1/vocabulary-settings")
                        .with(JwtFixtures.student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCardsPerDay\":5}"))
                .andExpect(jsonPath("$.newCardsPerDay").value(5));
        mvc.perform(put("/api/v1/vocabulary-settings")
                        .with(JwtFixtures.student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCardsPerDay\":40}"))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 7; i++) {
            addWord(user, "word" + (char) ('a' + i), null).andExpect(status().isCreated());
        }
        mvc.perform(get("/api/v1/review-cards/due").with(JwtFixtures.student(user)))
                .andExpect(jsonPath("$.newCount").value(5));
    }

    // ---- lifecycle and internal API ----

    @Test
    @DisplayName("docs/04 §5.11: account deletion removes the dictionary, sessions and settings")
    void accountDeletion() throws Exception {
        UUID user = UUID.randomUUID();
        addWord(user, "canopy", null).andExpect(status().isCreated());
        startSession(user);
        send(
                "toefly.profile.account-deletion-requested.v1",
                user,
                AccountDeletionRequestedV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setOccurredAt(Instant.now())
                        .setUserId(user)
                        .setDeletionId(UUID.randomUUID())
                        .setRequestedAt(Instant.now())
                        .setLegalDeadlineAt(Instant.now().plus(Duration.ofDays(30)))
                        .setRoles(List.of(com.toefly.events.common.Role.STUDENT))
                        .build());
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .until(() -> count("SELECT count(*) FROM vocabulary_entry WHERE user_id = :u", user) == 0
                        && count("SELECT count(*) FROM review_session WHERE user_id = :u", user) == 0);
    }

    @Test
    @DisplayName("docs/05 §9.3.4: internal operations need the SERVICE role")
    void internalNeedsService() throws Exception {
        UUID user = UUID.randomUUID();
        mvc.perform(get("/internal/v1/users/" + user + "/vocabulary-summary").with(JwtFixtures.student(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/v1/users/" + user + "/vocabulary-summary").with(service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entriesTotal").value(0));
    }

    // ---- contracts ----

    @TestFactory
    @DisplayName("ADR-0031: vocabulary events fulfil the contracts of their consumers")
    Stream<DynamicTest> eventContracts() {
        OutboxMessageReceiver receiver = new OutboxMessageReceiver(jdbc, registryUrl);
        return ContractTests.messages(CONTRACTS, this, receiver).onClose(receiver::close);
    }

    @TestFactory
    @DisplayName("ADR-0031: the internal API fulfils the contracts of tutor-agent and profile")
    Stream<DynamicTest> restContracts() throws Exception {
        prepareContractData();
        return Stream.of("tutor-agent-service", "profile-service")
                .flatMap(caller -> ContractTests.rest(CONTRACTS.resolve(caller), MockMvcExchanger.of(mvc, service())));
    }

    @Test
    @DisplayName("docs/11 §8.5: every vocabulary topic has a message contract")
    void contractsCoverTopics() {
        ContractCoverage.assertMessagesCover(CONTRACTS, publisher.topics());
    }

    void wordAdded() throws Exception {
        addWord(UUID.randomUUID(), "mitigate", "Urban forests mitigate the heat island effect.")
                .andExpect(status().isCreated());
    }

    void reviewCompleted() throws Exception {
        UUID user = UUID.randomUUID();
        addWord(user, "canopy", null).andExpect(status().isCreated());
        JsonNode session = startSession(user);
        grade(
                user,
                session.path("cards").get(0).path("id").asString(),
                "GOOD",
                session.path("id").asString(),
                0);
        complete(user, session.path("id").asString()).andExpect(status().isOk());
    }

    private void prepareContractData() throws Exception {
        jdbc.sql("DELETE FROM vocabulary_entry WHERE user_id = :u")
                .param("u", CONTRACT_STUDENT)
                .update();
        addWord(CONTRACT_STUDENT, "mitigate", "Urban forests mitigate the heat island effect.")
                .andExpect(status().isCreated());
        addWord(CONTRACT_STUDENT, "canopy", null).andExpect(status().isCreated());
        JsonNode session = startSession(CONTRACT_STUDENT);
        grade(
                CONTRACT_STUDENT,
                session.path("cards").get(0).path("id").asString(),
                "GOOD",
                session.path("id").asString(),
                0);
        jdbc.sql("DELETE FROM idempotency_record WHERE user_id = :u")
                .param("u", CONTRACT_STUDENT)
                .update();
    }

    // ---- helpers ----

    private ResultActions addWord(UUID user, String term, String context) throws Exception {
        String body = context == null ? "{\"term\":\"%s\"}".formatted(term) : """
                        {"term": "%s", "context": "%s", "sourceRef": {"itemSetId": "%s", "label": "Urban forests"}}
                        """.formatted(term, context, ITEM_SET);
        return mvc.perform(post("/api/v1/vocabulary-entries")
                .with(JwtFixtures.student(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode startSession(UUID user) throws Exception {
        return body(mvc.perform(post("/api/v1/review-sessions")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
    }

    private ResultActions grade(UUID user, String card, String rating, String session, int sequence) throws Exception {
        return mvc.perform(post("/api/v1/review-cards/" + card + "/reviews")
                .with(JwtFixtures.student(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"%s\",\"reviewSessionId\":\"%s\",\"sequenceNo\":%d,\"elapsedMs\":4000}"
                        .formatted(rating, session, sequence)));
    }

    private ResultActions complete(UUID user, String session) throws Exception {
        return mvc.perform(post("/api/v1/review-sessions/" + session + "/completion")
                .with(JwtFixtures.student(user))
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private ResultActions enroll(UUID user) throws Exception {
        return mvc.perform(post("/api/v1/word-sets/" + UNIT_1 + "/enrollment")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    private OutboxAssertions.PublishedEvent publishedOnce(String topic, String subject) {
        List<OutboxAssertions.PublishedEvent> events = OutboxAssertions.published(jdbc, topic).stream()
                .filter(event -> subject.equals(event.subject()))
                .toList();
        assertThat(events).as("%s for %s", topic, subject).hasSize(1);
        return events.getFirst();
    }

    private long count(String sql, Object user) {
        return jdbc.sql(sql).param("u", user).query(Long.class).single();
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void send(String topic, UUID key, SpecificRecord event) throws Exception {
        EventPayloadSerializer.SerializedPayload payload = serializer.serialize(topic, event);
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (var producer = new KafkaProducer<>(properties, new StringSerializer(), new ByteArraySerializer())) {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key.toString(), payload.bytes());
            record.headers()
                    .add(CloudEventHeaders.ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("ce_type", topic.getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }

    private static PreferencesUpdatedV1 preferences(UUID user, String zone) {
        return PreferencesUpdatedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setTimezone(zone)
                .setExplanationLanguage("en")
                .setMinutesPerDay(45)
                .setPreferredStudyTime("19:00")
                .build();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor service() {
        return JwtFixtures.withRoles(UUID.randomUUID(), "SERVICE");
    }
}
