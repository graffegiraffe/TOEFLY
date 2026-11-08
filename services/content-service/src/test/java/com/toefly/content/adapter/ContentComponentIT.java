package com.toefly.content.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toefly.common.test.ApicurioTestcontainersConfiguration;
import com.toefly.common.test.JwtFixtures;
import com.toefly.common.test.KafkaTestcontainersConfiguration;
import com.toefly.common.test.OutboxAssertions;
import com.toefly.common.test.PostgresTestcontainersConfiguration;
import com.toefly.common.test.contract.ContractCoverage;
import com.toefly.common.test.contract.ContractTests;
import com.toefly.common.test.contract.OutboxMessageReceiver;
import com.toefly.content.application.port.in.ImportReport;
import com.toefly.content.application.port.in.ImportSeedUseCase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Seed import, keyless delivery, search and moderation on a synthetic bundle with our own texts. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({
    PostgresTestcontainersConfiguration.class,
    KafkaTestcontainersConfiguration.class,
    ApicurioTestcontainersConfiguration.class
})
class ContentComponentIT {

    static final Path SEED = createSeed();

    @DynamicPropertySource
    static void seed(DynamicPropertyRegistry registry) {
        registry.add("toefly.content.seed.location", () -> "dir:" + SEED);
        registry.add("toefly.content.embeddings.url", () -> "http://localhost:1");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ImportSeedUseCase importer;

    @MockitoBean
    JwtDecoder jwtDecoder;

    static String itemSetId;

    @Test
    @Order(1)
    @DisplayName("US-28.01: the first import creates the sets and publishes item-set-published once per set")
    void firstImport() {
        ImportReport report = importer.run(Optional.empty(), false);

        assertThat(report.sources())
                .filteredOn(s -> s.sourceCode().equals("demo-reading"))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.status()).isEqualTo("DONE");
                    assertThat(s.created()).isEqualTo(4);
                });
        // a source whose file does not match its manifest hash fails alone and does not block the others
        assertThat(report.sources())
                .filteredOn(s -> s.sourceCode().equals("broken-source"))
                .singleElement()
                .satisfies(s -> assertThat(s.status()).isEqualTo("FAILED"));
        assertThat(OutboxAssertions.published(jdbc, "toefly.content.item-set-published.v1"))
                .hasSize(2);
        itemSetId = jdbc.sql("SELECT id::text FROM item_set WHERE seed_id = 'demo-reading/climate-001'")
                .query(String.class)
                .single();
    }

    @Test
    @Order(2)
    @DisplayName("a repeated import changes nothing (idempotent by bundle hash)")
    void secondImportIsSkipped() {
        ImportReport report = importer.run(Optional.empty(), false);

        assertThat(report.sources())
                .filteredOn(s -> s.sourceCode().equals("demo-reading"))
                .singleElement()
                .satisfies(s -> assertThat(s.status()).isEqualTo("SKIPPED"));
        assertThat(jdbc.sql("SELECT count(*) FROM item_set")
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
        assertThat(OutboxAssertions.published(jdbc, "toefly.content.item-set-published.v1"))
                .hasSize(2);
    }

    @Test
    @Order(3)
    @DisplayName("a student sees the source and no answer keys")
    void studentGetsKeylessSet() throws Exception {
        mvc.perform(get("/api/v1/item-sets/" + itemSetId).with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.attribution").value("Synthetic fixture, written for the tests"))
                .andExpect(jsonPath("$.payload.items[0].prompt").exists())
                .andExpect(jsonPath("$.payload.items[0].answerKey").doesNotExist())
                .andExpect(jsonPath("$.payload.items[0].explanation").doesNotExist());
    }

    @Test
    @Order(4)
    @DisplayName("full-text search finds a set by a word of its text and reports the mode")
    void searchFindsByKeyword() throws Exception {
        mvc.perform(get("/api/v1/item-sets/search")
                        .param("q", "photosynthesis")
                        .with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("FULL_TEXT"))
                .andExpect(jsonPath("$.hits[0].itemSet.id").value(itemSetId));
    }

    @Test
    @Order(5)
    @DisplayName("the catalog filters by legacy and paginates by cursor")
    void catalogFilters() throws Exception {
        mvc.perform(get("/api/v1/item-sets").param("limit", "1").with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.nextCursor").isNotEmpty());
        mvc.perform(get("/api/v1/item-sets").param("legacy", "true").with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/item-sets").param("cursor", "garbage").with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    @Order(6)
    @DisplayName("a student cannot reach the admin API")
    void studentIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/item-sets").with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("US-28.01: a hidden set is not given to students and item-set-hidden is published")
    void hideSet() throws Exception {
        mvc.perform(patch("/api/v1/admin/item-sets/" + itemSetId)
                        .with(JwtFixtures.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"));

        mvc.perform(get("/api/v1/item-sets/" + itemSetId).with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_SET_UNAVAILABLE"));
        mvc.perform(get("/api/v1/item-sets/search")
                        .param("q", "photosynthesis")
                        .with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(jsonPath("$.hits.length()").value(0));
        assertThat(OutboxAssertions.published(jdbc, "toefly.content.item-set-hidden.v1"))
                .hasSize(1);
    }

    @Test
    @Order(8)
    @DisplayName("a changed seed file updates content and leaves the administrator's HIDDEN status alone")
    void reimportKeepsAdminStatus() throws Exception {
        rewriteClimateTopic("Climate and plants, revised");
        importer.run(Optional.empty(), false);

        assertThat(jdbc.sql("SELECT status FROM item_set WHERE id = CAST(:id AS uuid)")
                        .param("id", itemSetId)
                        .query(String.class)
                        .single())
                .isEqualTo("HIDDEN");
        assertThat(jdbc.sql("SELECT version FROM item_set WHERE id = CAST(:id AS uuid)")
                        .param("id", itemSetId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM item_set_revision WHERE item_set_id = CAST(:id AS uuid)")
                        .param("id", itemSetId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    @Order(9)
    @DisplayName("a report is accepted once and an administrator can close it")
    void reports() throws Exception {
        UUID student = UUID.randomUUID();
        String body = "{\"itemSetId\":\"" + itemSetId + "\",\"reason\":\"TYPO\",\"itemOrdinal\":1}";
        String created = mvc.perform(post("/api/v1/item-reports")
                        .with(JwtFixtures.student(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(post("/api/v1/item-reports")
                        .with(JwtFixtures.student(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());

        String reportId = tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(created)
                .get("id")
                .asString();
        mvc.perform(patch("/api/v1/admin/item-reports/" + reportId)
                        .with(JwtFixtures.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"DISMISSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    @Order(9)
    @DisplayName("unknown ids answer 404 Problem Details")
    void notFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        mvc.perform(get("/api/v1/item-sets/" + unknown).with(JwtFixtures.student(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/v1/item-reports")
                        .with(JwtFixtures.student(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemSetId\":\"" + unknown + "\",\"reason\":\"TYPO\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/sources/nope/hide").with(JwtFixtures.admin(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/item-reports/" + unknown)
                        .with(JwtFixtures.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"RESOLVED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(10)
    @DisplayName("US-28.01: hiding a source hides all of its approved sets")
    void takedown() throws Exception {
        mvc.perform(post("/api/v1/admin/sources/demo-reading/hide").with(JwtFixtures.admin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(1));
    }

    @Test
    @Order(11)
    @DisplayName("units: an unreviewed one waits for approval, a reviewed one is served at once")
    void units() throws Exception {
        UUID student = UUID.randomUUID();
        mvc.perform(get("/api/v1/vocabulary-units").with(JwtFixtures.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].words[0].word").value("mural"));
        mvc.perform(get("/api/v1/grammar-units").with(JwtFixtures.student(student)))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/admin/grammar-units").with(JwtFixtures.student(student)))
                .andExpect(status().isForbidden());

        String id = jdbc.sql("SELECT id::text FROM grammar_unit")
                .query(String.class)
                .single();
        mvc.perform(get("/api/v1/grammar-units/" + id).with(JwtFixtures.student(student)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/grammar-units").with(JwtFixtures.admin(UUID.randomUUID())))
                .andExpect(jsonPath("$.items[0].status").value("IN_REVIEW"));
        mvc.perform(post("/api/v1/admin/grammar-units/" + id + "/approval").with(JwtFixtures.admin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/v1/grammar-units")
                        .param("framework", "ROUND_UP_5")
                        .with(JwtFixtures.student(student)))
                .andExpect(jsonPath("$.items[0].title").value("Present forms"));
        mvc.perform(post("/api/v1/admin/vocabulary-units/" + UUID.randomUUID() + "/approval")
                        .with(JwtFixtures.admin(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(12)
    @DisplayName("a repeated import keeps units unchanged")
    void unitsAreIdempotent() {
        importer.run(Optional.empty(), false);
        assertThat(jdbc.sql("SELECT count(*) FROM grammar_unit")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM vocabulary_unit")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    // Contracts of the consumers of content events (ADR-0031, docs/11-quality-testing.md section 8.4).

    static final Path CONTRACTS = Path.of("src/test/resources/contracts");

    @Value("${toefly.events.registry-url}")
    String registryUrl;

    @TestFactory
    @Order(20)
    @DisplayName("ADR-0031 content events fulfil the contracts of their consumers")
    Stream<DynamicTest> eventContracts() {
        OutboxMessageReceiver receiver = new OutboxMessageReceiver(jdbc, registryUrl);
        return ContractTests.messages(CONTRACTS, this, receiver).onClose(receiver::close);
    }

    @Test
    @Order(21)
    @DisplayName("docs/11 section 8.5: every published content topic has a message contract")
    void contractsCoverTopics() {
        ContractCoverage.assertMessagesCover(
                CONTRACTS, List.of("toefly.content.item-set-published.v1", "toefly.content.item-set-hidden.v1"));
    }

    void itemSetPublished() throws Exception {
        String id = librarySetId();
        setStatus(id, "HIDDEN");
        setStatus(id, "APPROVED");
    }

    void itemSetHidden() throws Exception {
        String id = librarySetId();
        setStatus(id, "APPROVED");
        setStatus(id, "HIDDEN");
    }

    private String librarySetId() {
        return jdbc.sql("SELECT id::text FROM item_set WHERE seed_id = 'demo-reading/library-002'")
                .query(String.class)
                .single();
    }

    /** Moves the set to the status; a set that is already there stays unchanged. */
    private void setStatus(String id, String status) throws Exception {
        String current = jdbc.sql("SELECT status FROM item_set WHERE id = CAST(:id AS uuid)")
                .param("id", id)
                .query(String.class)
                .single();
        if (!current.equals(status)) {
            mvc.perform(patch("/api/v1/admin/item-sets/" + id)
                            .with(JwtFixtures.admin(UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + status + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    // ---- synthetic bundle ----

    private static Path createSeed() {
        try {
            Path root = Files.createTempDirectory("toefly-seed-test");
            Path dir = Files.createDirectories(root.resolve("demo-reading/item-sets"));
            Files.writeString(root.resolve("demo-reading/source.json"), """
                    {"sourceCode":"demo-reading","title":"Demo reading","publisher":"TOEFLY tests","year":2026,
                     "url":"https://example.test/demo","examFormat":"TOEFL_2026",
                     "attributionText":"Synthetic fixture, written for the tests","extra":{}}
                    """);
            Files.writeString(dir.resolve("climate-001.json"), climate("Climate and plants"));
            Files.writeString(dir.resolve("library-002.json"), library());
            Files.createDirectories(root.resolve("demo-reading/grammar-units"));
            Files.createDirectories(root.resolve("demo-reading/vocabulary-units"));
            Files.writeString(root.resolve("demo-reading/grammar-units/grammar-u01.json"), """
                    {"schemaVersion":"1.0","kind":"GRAMMAR_UNIT","seedId":"demo-reading/grammar-u01",
                     "sourceCode":"demo-reading","extraction":{"method":"TEXT_LAYER","reviewed":false},
                     "unit":{"framework":"ROUND_UP_5","unitNo":1,"title":"Present forms","topicCodes":["present-forms"],
                             "ruleCard":null,"progressCheckNo":null,"exercises":[]}}
                    """);
            Files.writeString(root.resolve("demo-reading/vocabulary-units/vocab-u01.json"), """
                    {"schemaVersion":"1.0","kind":"VOCABULARY_UNIT","seedId":"demo-reading/vocab-u01",
                     "sourceCode":"demo-reading","extraction":{"method":"TEXT_LAYER","reviewed":true},
                     "unit":{"unitNo":1,"theme":"ARTS","title":"Visual art","exercises":[],
                             "words":[{"word":"mural","pos":"n","definition":"a painting on a wall",
                                       "example":"The mural covers the whole library wall."}]}}
                    """);
            Files.createDirectories(root.resolve("broken-source/item-sets"));
            Files.writeString(root.resolve("broken-source/source.json"), """
                    {"sourceCode":"broken-source","title":"Broken","publisher":"TOEFLY tests","year":2026,
                     "examFormat":"TOEFL_2026","attributionText":"Synthetic fixture","extra":{}}
                    """);
            Files.writeString(root.resolve("broken-source/item-sets/x-001.json"), "{}");
            writeManifest(root);
            return root;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void rewriteClimateTopic(String topic) throws IOException {
        Files.writeString(SEED.resolve("demo-reading/item-sets/climate-001.json"), climate(topic));
        Files.writeString(
                SEED.resolve("demo-reading/item-sets/climate-001.json"),
                climate(topic).replace("Plants turn light into food", "Plants turn sunlight into sugar"));
        writeManifest(SEED);
    }

    private static void writeManifest(Path root) throws IOException {
        StringBuilder files = new StringBuilder();
        StringBuilder bundle = new StringBuilder();
        String[][] entries = {
            {"item-sets", "climate-001", "ITEM_SET"},
            {"item-sets", "library-002", "ITEM_SET"},
            {"grammar-units", "grammar-u01", "GRAMMAR_UNIT"},
            {"vocabulary-units", "vocab-u01", "VOCABULARY_UNIT"}
        };
        for (String[] entry : entries) {
            String name = entry[1];
            Path f = root.resolve("demo-reading/" + entry[0] + "/" + name + ".json");
            String sha = "sha256:" + sha256(Files.readAllBytes(f));
            bundle.append(name).append(sha);
            files.append(files.isEmpty() ? "" : ",")
                    .append("{\"path\":\"demo-reading/")
                    .append(entry[0])
                    .append("/")
                    .append(name)
                    .append(".json\",\"seedId\":\"demo-reading/")
                    .append(name)
                    .append("\",\"kind\":\"")
                    .append(entry[2])
                    .append("\",\"sha256\":\"")
                    .append(sha)
                    .append("\"}");
        }
        Files.writeString(
                root.resolve("manifest.json"),
                "{\"manifestVersion\":1,\"sources\":[{\"sourceCode\":\"demo-reading\",\"bundleSha256\":\"sha256:"
                        + sha256(bundle.toString().getBytes(StandardCharsets.UTF_8)) + "\",\"files\":[" + files
                        + "]},"
                        + "{\"sourceCode\":\"broken-source\",\"bundleSha256\":\"sha256:0\",\"files\":["
                        + "{\"path\":\"broken-source/item-sets/x-001.json\",\"seedId\":\"broken-source/x-001\","
                        + "\"kind\":\"ITEM_SET\",\"sha256\":\"sha256:wrong\"}]}]}");
    }

    private static String climate(String topic) {
        return """
                {"schemaVersion":"1.0","kind":"ITEM_SET","seedId":"demo-reading/climate-001","sourceCode":"demo-reading",
                 "locator":{"pages":[3,3],"questionNumbers":[1]},"extraction":{"method":"TEXT_LAYER","reviewed":true},
                 "itemSet":{"taskType":"READ_ACADEMIC_PASSAGE","examFormat":"TOEFL_2026","section":"READING",
                  "provenance":"EXTRACTED","cefr":"B2","topic":"%s","skills":["reading.detail"],
                  "payload":{"stimulus":{"title":"Plants and light",
                    "paragraphs":["Plants turn light into food by photosynthesis, a process that needs water."]},
                   "items":[{"ordinal":1,"subskill":"DETAIL","prompt":"What do plants need besides light?",
                     "options":[{"key":"A","text":"Water","rationale":"The passage says the process needs water."},
                                {"key":"B","text":"Sand","rationale":"Sand is never mentioned."}],
                     "answerKey":"A","explanation":{"summary":"The last clause names water."}}]}}}
                """.formatted(topic);
    }

    private static String library() {
        return """
                {"schemaVersion":"1.0","kind":"ITEM_SET","seedId":"demo-reading/library-002","sourceCode":"demo-reading",
                 "locator":{"pages":[4,4],"questionNumbers":[2]},"extraction":{"method":"TEXT_LAYER","reviewed":true},
                 "itemSet":{"taskType":"READ_DAILY_LIFE","examFormat":"TOEFL_2026","section":"READING",
                  "provenance":"EXTRACTED","cefr":"B1","topic":"campus library","skills":["reading.detail"],
                  "payload":{"stimulus":{"genre":"NOTICE","title":"Opening hours",
                    "body":"The library opens at nine and closes at six on weekdays."},
                   "items":[{"ordinal":1,"subskill":"FACTUAL","prompt":"When does the library close?",
                     "options":[{"key":"A","text":"At six","rationale":"Stated in the notice."},
                                {"key":"B","text":"At nine","rationale":"That is the opening time."}],
                     "answerKey":"A","explanation":{"summary":"Closing time is six."}}]}}}
                """;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
