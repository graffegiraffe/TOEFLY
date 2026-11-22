package com.toefly.billing.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.LifecycleUseCase;
import com.toefly.billing.application.port.out.BillingEventPublisherPort;
import com.toefly.billing.application.port.out.ClassroomPort;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.common.events.CloudEventHeaders;
import com.toefly.common.events.EventPayloadSerializer;
import com.toefly.common.test.ApicurioTestcontainersConfiguration;
import com.toefly.common.test.JwtFixtures;
import com.toefly.common.test.KafkaTestcontainersConfiguration;
import com.toefly.common.test.PostgresTestcontainersConfiguration;
import com.toefly.common.test.contract.ContractCoverage;
import com.toefly.common.test.contract.ContractTests;
import com.toefly.common.test.contract.MockMvcExchanger;
import com.toefly.common.test.contract.OutboxMessageReceiver;
import com.toefly.events.common.Role;
import com.toefly.events.profile.ProfileCreatedV1;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
 * billing-service against PostgreSQL, Kafka and Apicurio; Stripe and bePaid answer from WireMock, webhooks are signed
 * like the providers sign them, App Store transactions are signed with a test StoreKit chain.
 */
@SpringBootTest(
        properties = {
            "toefly.billing.jobs.enabled=false",
            "toefly.billing.saga.max-attempts=2",
            "toefly.billing.saga.first-delay=0s",
            "toefly.billing.providers.stripe.enabled=true",
            "toefly.billing.providers.stripe.secret-key=sk_test_component",
            "toefly.billing.providers.stripe.webhook-secret=" + BillingComponentIT.STRIPE_SECRET,
            "toefly.billing.providers.bepaid.enabled=true",
            "toefly.billing.providers.bepaid.shop-id=component-test-shop",
            "toefly.billing.providers.bepaid.secret-key=component-test-secret",
            "toefly.billing.providers.app-store.enabled=true",
            "toefly.billing.providers.app-store.root-certificate=src/test/resources/appstore/root.pem"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import({
    PostgresTestcontainersConfiguration.class,
    KafkaTestcontainersConfiguration.class,
    ApicurioTestcontainersConfiguration.class
})
class BillingComponentIT {

    static final String STRIPE_SECRET = "whsec_component_test";
    static final String RETURN = "http://app.toefly.localhost:5173/billing/checkout/result";
    static final Path CONTRACTS = Path.of("src/test/resources/contracts");
    static final Duration KAFKA_WAIT = Duration.ofSeconds(90);
    static final JsonMapper JSON = JsonMapper.builder().build();

    static final WireMockServer PROVIDERS =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final KeyPair BEPAID_KEYS = rsa();

    static {
        PROVIDERS.start();
    }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        registry.add("toefly.billing.providers.stripe.api-url", PROVIDERS::baseUrl);
        registry.add("toefly.billing.providers.bepaid.checkout-url", PROVIDERS::baseUrl);
        registry.add("toefly.billing.providers.bepaid.gateway-url", PROVIDERS::baseUrl);
        registry.add(
                "toefly.billing.providers.bepaid.public-key",
                () -> Base64.getEncoder().encodeToString(BEPAID_KEYS.getPublic().getEncoded()));
    }

    @AfterAll
    static void stop() {
        PROVIDERS.stop();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    LifecycleUseCase lifecycle;

    @Autowired
    BillingEventPublisherPort publisher;

    @Autowired
    EventPayloadSerializer serializer;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    KafkaListenerEndpointRegistry listenerRegistry;

    @Value("${toefly.events.registry-url}")
    String registryUrl;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    ClassroomPort classroom;

    @BeforeEach
    void setUp() {
        reset(classroom);
        PROVIDERS.resetAll();
        PROVIDERS.stubFor(WireMock.post(urlEqualTo("/v1/checkout/sessions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"cs_test_{{randomValue length=16 type='ALPHANUMERIC'}}",
                                 "url":"https://checkout.stripe.com/c/pay/cs_test_component","expires_at":%d}
                                """.formatted(Instant.now().plusSeconds(3600).getEpochSecond()))
                        .withTransformers("response-template")));
        PROVIDERS.stubFor(WireMock.post(urlEqualTo("/ctp/api/checkouts"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"checkout":{"token":"tok_{{randomValue length=16 type='ALPHANUMERIC'}}",
                                 "redirect_url":"https://checkout.bepaid.by/v2/checkout?token=tok_bepaid"}}
                                """)
                        .withTransformers("response-template")));
        PROVIDERS.stubFor(WireMock.get(urlPathMatching("/v1/invoices/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"in_x\",\"payment_intent\":\"pi_component\"}")));
        PROVIDERS.stubFor(WireMock.post(urlEqualTo("/v1/refunds"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"re_1\"}")));
        PROVIDERS.stubFor(WireMock.delete(urlPathMatching("/v1/subscriptions/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
        PROVIDERS.stubFor(WireMock.post(urlPathMatching("/v1/subscriptions/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
        awaitListenerAssignment();
    }

    /** Slow CI runners join the consumer group late; tests start once every listener owns its partitions. */
    private void awaitListenerAssignment() {
        Awaitility.await()
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> listenerRegistry.getAllListenerContainers().stream()
                        .filter(MessageListenerContainer::isRunning)
                        .allMatch(c -> c.getAssignedPartitions() != null
                                && !c.getAssignedPartitions().isEmpty()));
    }

    // ---- Stripe ----

    @Test
    @DisplayName(
            "AC-27.03.1, AC-27.03.2, AC-27.03.4, T-F6-02: Stripe Checkout, a signed webhook, rights; a repeat changes nothing")
    void stripeCheckoutActivatesPro() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode session = checkout(user, "STUDENT_PRO", "STRIPE");
        assertThat(session.path("redirectUrl").asString()).startsWith("https://checkout.stripe.com/");
        assertThat(session.path("testMode").asBoolean()).isTrue();
        String sessionId = session.path("id").asString();
        mvc.perform(get("/api/v1/checkout-sessions/" + sessionId).with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        PROVIDERS.verify(postRequestedFor(urlEqualTo("/v1/checkout/sessions"))
                .withRequestBody(
                        com.github.tomakehurst.wiremock.client.WireMock.containing("client_reference_id=" + user)));

        byte[] event = stripeCompleted("evt_" + user, sessionId, user, "STUDENT_PRO", "in_" + user);
        stripeWebhook(event).andExpect(status().isOk());
        stripeWebhook(event).andExpect(status().isOk());

        mvc.perform(get("/api/v1/checkout-sessions/" + sessionId).with(JwtFixtures.student(user)))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/subscriptions/current").with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.sagaStatus").value("COMPLETED"));
        mvc.perform(get("/api/v1/entitlements").with(JwtFixtures.student(user)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.planTierCode").value("STUDENT_PRO"))
                .andExpect(jsonPath("$.entitlements[?(@ == 'MOCK_EXAM')]").exists());
        mvc.perform(get("/api/v1/payments").with(JwtFixtures.student(user)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].amountMinor").value(1200))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"));
        assertThat(count("SELECT count(*) FROM webhook_event WHERE provider_event_id = :id", "evt_" + user))
                .isEqualTo(1);
        assertThat(outbox(user, "toefly.billing.subscription-activated.v1")).isEqualTo(1);
        assertThat(outbox(user, "toefly.billing.payment-succeeded.v1")).isEqualTo(1);
        assertThat(outbox(user, "toefly.billing.entitlements-changed.v1")).isEqualTo(1);
    }

    @Test
    @DisplayName("T-F6-01: a webhook with a wrong signature is rejected before anything is stored")
    void wrongSignatureIsRejected() throws Exception {
        byte[] event = stripeCompleted(
                "evt_forged", UUID.randomUUID().toString(), UUID.randomUUID(), "STUDENT_PRO", "in_forged");
        mvc.perform(post("/api/v1/billing/webhooks/stripe")
                        .header("Stripe-Signature", "t=" + Instant.now().getEpochSecond() + ",v1=" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        mvc.perform(post("/api/v1/billing/webhooks/stripe")
                        .header("Stripe-Signature", sign(event, Instant.now().minus(Duration.ofMinutes(10))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event))
                .andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM webhook_event WHERE provider_event_id = :id", "evt_forged"))
                .isZero();
    }

    @Test
    @DisplayName("phase 11a DoD, AC-27.03.3: a tutor saga failing at the workspace is compensated with a refund")
    void failingSagaIsCompensated() throws Exception {
        UUID tutor = UUID.randomUUID();
        doThrow(new IllegalStateException("classroom down"))
                .when(classroom)
                .putWorkspace(eq(tutor), eq(true), anyInt(), any(PlanTierCode.class));
        JsonNode session = checkoutAs(JwtFixtures.tutor(tutor), "TUTOR", "STRIPE");

        stripeWebhook(stripeCompleted("evt_" + tutor, session.path("id").asString(), tutor, "TUTOR", "in_" + tutor))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("SELECT status FROM saga_instance WHERE user_id = :user")
                        .param("user", tutor)
                        .query(String.class)
                        .single())
                .isEqualTo("FAILED");
        PROVIDERS.verify(postRequestedFor(urlEqualTo("/v1/refunds")));
        PROVIDERS.verify(deleteRequestedFor(urlPathMatching("/v1/subscriptions/sub_" + tutor)));
        Mockito.verify(classroom, Mockito.atLeastOnce())
                .putWorkspace(eq(tutor), eq(false), anyInt(), any(PlanTierCode.class));
        assertThat(jdbc.sql("SELECT status FROM payment WHERE user_id = :user")
                        .param("user", tutor)
                        .query(String.class)
                        .single())
                .isEqualTo("REFUNDED");
        assertThat(count(
                        "SELECT count(*) FROM seat s JOIN subscription u ON u.id = s.subscription_id "
                                + "WHERE u.user_id = :id",
                        tutor))
                .isZero();
        mvc.perform(get("/api/v1/subscriptions/current").with(JwtFixtures.tutor(tutor)))
                .andExpect(jsonPath("$.status").isEmpty());
        mvc.perform(get("/api/v1/checkout-sessions/" + session.path("id").asString())
                        .with(JwtFixtures.tutor(tutor)))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("SAGA_COMPENSATED"));
        assertThat(outbox(tutor, "toefly.billing.payment-failed.v1")).isEqualTo(1);
        assertThat(outbox(tutor, "toefly.billing.subscription-canceled.v1")).isEqualTo(1);
        assertThat(outbox(tutor, "toefly.billing.subscription-activated.v1")).isZero();
    }

    // ---- bePaid ----

    @Test
    @DisplayName("AC-27.03.1, AC-27.03.2: bePaid hosted page and an RSA-signed notification activate Pro")
    void bepaidPayment() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode session = checkout(user, "STUDENT_PRO", "BEPAID");
        assertThat(session.path("redirectUrl").asString()).startsWith("https://checkout.bepaid.by/");

        byte[] notification = """
                {"transaction":{"uid":"bp-%s","status":"successful","type":"payment","amount":1200,
                 "currency":"EUR","tracking_id":"%s","test":true,"message":"Successfully processed",
                 "paid_at":"2026-11-21T10:00:00Z"}}
                """.formatted(user, session.path("id").asString()).getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/api/v1/billing/webhooks/bepaid")
                        .header("Content-Signature", "aW52YWxpZA==")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notification))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/billing/webhooks/bepaid")
                        .header("Content-Signature", rsaSign(notification))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notification))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/subscriptions/current").with(JwtFixtures.student(user)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.provider").value("BEPAID"));
    }

    // ---- App Store ----

    @Test
    @DisplayName("AC-27.04.2: a StoreKit 2 JWS verified by its chain runs the same saga; a foreign chain is refused")
    void appStoreTransaction() throws Exception {
        UUID user = UUID.randomUUID();
        String foreign = jws(user, "2000000" + user.toString().substring(0, 8), "app.toefly.pro.monthly", false);
        mvc.perform(post("/api/v1/billing/app-store/transactions")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("signedTransactionInfo", foreign))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        String someoneElse =
                jws(UUID.randomUUID(), "3000000" + user.toString().substring(0, 8), "app.toefly.pro.monthly", true);
        mvc.perform(post("/api/v1/billing/app-store/transactions")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("signedTransactionInfo", someoneElse))))
                .andExpect(status().isConflict());

        String signed = jws(user, "1000000" + user.toString().substring(0, 8), "app.toefly.pro.monthly", true);
        mvc.perform(post("/api/v1/billing/app-store/transactions")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("signedTransactionInfo", signed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.provider").value("APP_STORE"));
        mvc.perform(post("/api/v1/subscriptions/current/cancellation")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MANAGE_IN_APP_STORE"));
    }

    // ---- Kafka ----

    @Test
    @DisplayName("AC-27.02.1, AC-27.02.5: profile-created from Kafka starts the trial once; a tutor gets none")
    void trialFromKafka() throws Exception {
        UUID student = UUID.randomUUID();
        UUID tutor = UUID.randomUUID();
        ProfileCreatedV1 event = profileCreated(student, Role.STUDENT);
        send("toefly.profile.profile-created.v1", student.toString(), event.getEventId(), event);
        send("toefly.profile.profile-created.v1", student.toString(), event.getEventId(), event);
        ProfileCreatedV1 tutorEvent = profileCreated(tutor, Role.TUTOR);
        send("toefly.profile.profile-created.v1", tutor.toString(), tutorEvent.getEventId(), tutorEvent);

        Awaitility.await()
                .atMost(KAFKA_WAIT)
                .untilAsserted(() -> assertThat(outbox(tutor, "toefly.billing.entitlements-changed.v1"))
                        .isEqualTo(1));
        mvc.perform(get("/api/v1/subscriptions/current").with(JwtFixtures.student(student)))
                .andExpect(jsonPath("$.status").value("TRIALING"))
                .andExpect(jsonPath("$.source").value("TRIAL"));
        assertThat(count("SELECT count(*) FROM subscription WHERE user_id = :id", student))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM subscription WHERE user_id = :id", tutor))
                .isZero();
        assertThat(outbox(student, "toefly.billing.subscription-activated.v1")).isEqualTo(1);
    }

    // ---- API rules ----

    @Test
    @DisplayName("docs/05 §6.12: problems for missing keys, foreign return URLs, roles and unknown users")
    void apiRules() throws Exception {
        UUID user = UUID.randomUUID();
        mvc.perform(get("/api/v1/plan-tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[1].priceMinor").value(1200))
                .andExpect(jsonPath("$.providers[0].code").value("STRIPE"))
                .andExpect(jsonPath("$.providers[0].mode").value("TEST"));
        mvc.perform(post("/api/v1/checkout-sessions")
                        .with(JwtFixtures.student(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("STUDENT_PRO", "STRIPE", RETURN)))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/v1/checkout-sessions")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("STUDENT_PRO", "STRIPE", "https://evil.example/billing")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RETURN_URL_NOT_ALLOWED"));
        mvc.perform(post("/api/v1/checkout-sessions")
                        .with(JwtFixtures.student(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("TUTOR", "STRIPE", RETURN)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/subscriptions").with(JwtFixtures.student(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/subscriptions?limit=1").with(JwtFixtures.admin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/internal/v1/users/" + UUID.randomUUID() + "/entitlements")
                        .with(service()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/v1/users/" + user + "/entitlements").with(JwtFixtures.student(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin grant: a manual tier until a date, then the saga list and retry rules")
    void adminGrant() throws Exception {
        UUID user = UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/users/" + user + "/grants")
                        .with(JwtFixtures.admin(UUID.randomUUID()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planTierCode":"STUDENT_PRO","until":"%s","reason":"Pilot school"}
                                """.formatted(Instant.now().plus(Duration.ofDays(30)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planTierCode").value("STUDENT_PRO"))
                .andExpect(jsonPath("$.grants[0].source").value("MANUAL"));
        mvc.perform(get("/api/v1/admin/sagas").with(JwtFixtures.admin(UUID.randomUUID())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/sagas/" + UUID.randomUUID() + "/retry")
                        .with(JwtFixtures.admin(UUID.randomUUID()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    // ---- contracts ----

    @TestFactory
    @DisplayName("ADR-0031: billing events fulfil the contracts of their consumers")
    Stream<DynamicTest> eventContracts() {
        OutboxMessageReceiver receiver = new OutboxMessageReceiver(jdbc, registryUrl);
        return ContractTests.messages(CONTRACTS, this, receiver).onClose(receiver::close);
    }

    @TestFactory
    @DisplayName("ADR-0031: the internal API fulfils the contracts of api-gateway, classroom and profile")
    Stream<DynamicTest> restContracts() throws Exception {
        prepareContractData();
        return Stream.of("api-gateway", "classroom-service", "profile-service")
                .flatMap(caller -> ContractTests.rest(CONTRACTS.resolve(caller), MockMvcExchanger.of(mvc, service())));
    }

    @Test
    @DisplayName("docs/11 §8.5: every billing topic has a message contract")
    void contractsCoverTopics() {
        ContractCoverage.assertMessagesCover(CONTRACTS, publisher.topics());
    }

    void entitlementsChanged() {
        lifecycle.profileCreated(UUID.randomUUID(), Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());
    }

    void subscriptionActivated() {
        entitlementsChanged();
    }

    void trialEnding() {
        UUID user = UUID.randomUUID();
        lifecycle.profileCreated(user, Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());
        jdbc.sql("UPDATE subscription SET trial_ends_at = now() + interval '1 day', "
                        + "current_period_end = now() + interval '1 day' WHERE user_id = :user")
                .param("user", user)
                .update();
        lifecycle.runTimers();
    }

    void subscriptionCanceled() {
        UUID user = UUID.randomUUID();
        lifecycle.profileCreated(user, Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());
        jdbc.sql("UPDATE subscription SET trial_ends_at = now() - interval '1 minute', "
                        + "current_period_end = now() - interval '1 minute' WHERE user_id = :user")
                .param("user", user)
                .update();
        lifecycle.runTimers();
    }

    void paymentSucceeded() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode session = checkout(user, "STUDENT_PRO", "STRIPE");
        stripeWebhook(stripeCompleted(
                        "evt_c" + user, session.path("id").asString(), user, "STUDENT_PRO", "in_c" + user))
                .andExpect(status().isOk());
    }

    void paymentFailed() throws Exception {
        UUID user = UUID.randomUUID();
        JsonNode session = checkout(user, "STUDENT_PRO", "STRIPE");
        byte[] event = """
                {"id":"evt_d%s","type":"payment_intent.payment_failed","created":%d,"livemode":false,
                 "data":{"object":{"id":"pi_d%s","metadata":{"checkoutSessionId":"%s"},
                 "last_payment_error":{"code":"card_declined","decline_code":"generic_decline",
                 "message":"Your card was declined."}}}}
                """.formatted(
                        user,
                        Instant.now().getEpochSecond(),
                        user,
                        session.path("id").asString())
                .getBytes(StandardCharsets.UTF_8);
        stripeWebhook(event).andExpect(status().isOk());
    }

    private void prepareContractData() throws Exception {
        UUID student = UUID.fromString("3c0b7a52-9d1e-4f6a-8b2c-5e7d9f1a2b3c");
        if (count("SELECT count(*) FROM subscription WHERE user_id = :id", student) == 0) {
            lifecycle.profileCreated(student, Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());
        }
        UUID tutor = UUID.fromString("8e4f2a1b-6c3d-4e5f-9a0b-1c2d3e4f5a6b");
        if (count("SELECT count(*) FROM subscription WHERE user_id = :id", tutor) == 0) {
            JsonNode session = checkoutAs(JwtFixtures.tutor(tutor), "TUTOR", "STRIPE");
            stripeWebhook(stripeCompleted(
                            "evt_t" + tutor, session.path("id").asString(), tutor, "TUTOR", "in_t" + tutor))
                    .andExpect(status().isOk());
        }
    }

    // ---- helpers ----

    private JsonNode checkout(UUID user, String tier, String provider) throws Exception {
        return checkoutAs(JwtFixtures.student(user), tier, provider);
    }

    private JsonNode checkoutAs(
            org.springframework.test.web.servlet.request.RequestPostProcessor who, String tier, String provider)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/checkout-sessions")
                        .with(who)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(tier, provider, RETURN)))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static String checkoutBody(String tier, String provider, String returnUrl) {
        return """
                {"planTierCode":"%s","provider":"%s","returnUrl":"%s"}
                """.formatted(tier, provider, returnUrl);
    }

    /** A recorded checkout.session.completed of Stripe (API 2025), with this test's ids. */
    private static byte[] stripeCompleted(String eventId, String sessionId, UUID user, String tier, String invoice) {
        return """
                {"id":"%s","object":"event","type":"checkout.session.completed","created":%d,"livemode":false,
                 "data":{"object":{"id":"cs_test_component","object":"checkout.session","client_reference_id":"%s",
                 "metadata":{"checkoutSessionId":"%s","planTierCode":"%s","userId":"%s"},
                 "subscription":"sub_%s","customer":"cus_%s","invoice":"%s","payment_intent":null,
                 "payment_status":"paid","status":"complete","amount_total":%d,"currency":"eur"}}}
                """.formatted(
                        eventId,
                        Instant.now().getEpochSecond(),
                        user,
                        sessionId,
                        tier,
                        user,
                        user,
                        user,
                        invoice,
                        tier.equals("TUTOR") ? 2900 : 1200)
                .getBytes(StandardCharsets.UTF_8);
    }

    private org.springframework.test.web.servlet.ResultActions stripeWebhook(byte[] body) throws Exception {
        return mvc.perform(post("/api/v1/billing/webhooks/stripe")
                .header("Stripe-Signature", sign(body, Instant.now()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String sign(byte[] body, Instant at) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(STRIPE_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signed = at.getEpochSecond() + "." + new String(body, StandardCharsets.UTF_8);
        return "t=" + at.getEpochSecond() + ",v1="
                + HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
    }

    private static String rsaSign(byte[] body) throws Exception {
        Signature rsa = Signature.getInstance("SHA256withRSA");
        rsa.initSign(BEPAID_KEYS.getPrivate());
        rsa.update(body);
        return Base64.getEncoder().encodeToString(rsa.sign());
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A StoreKit 2 transaction signed by the test chain (trusted) or by a self-signed root (untrusted). */
    private static String jws(UUID accountToken, String transactionId, String product, boolean trusted)
            throws Exception {
        Path dir = Path.of("src/test/resources/appstore");
        String leaf = der(Files.readString(dir.resolve("leaf.pem")));
        String root = der(Files.readString(dir.resolve(trusted ? "root.pem" : "untrusted-root.pem")));
        String header = b64(JSON.writeValueAsBytes(Map.of("alg", "ES256", "x5c", List.of(leaf, root))));
        long now = Instant.now().toEpochMilli();
        String payload = b64(JSON.writeValueAsBytes(Map.of(
                "transactionId",
                transactionId,
                "originalTransactionId",
                transactionId,
                "productId",
                product,
                "appAccountToken",
                accountToken.toString(),
                "purchaseDate",
                now,
                "expiresDate",
                now + Duration.ofDays(30).toMillis(),
                "environment",
                "Xcode",
                "price",
                11990,
                "currency",
                "EUR")));
        PrivateKey key = KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(der(Files.readString(dir.resolve("leaf.key"))))));
        Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
        es256.initSign(key);
        es256.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + b64(es256.sign());
    }

    private static String der(String pem) {
        return pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ProfileCreatedV1 profileCreated(UUID user, Role role) {
        return ProfileCreatedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setUserId(user)
                .setRoles(List.of(role))
                .setEmailVerified(true)
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

    private int outbox(UUID user, String topic) {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE partition_key = :user AND topic = :topic")
                .param("user", user.toString())
                .param("topic", topic)
                .query(Integer.class)
                .single();
    }

    private int count(String sql, Object id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor service() {
        return JwtFixtures.withRoles(UUID.randomUUID(), "SERVICE");
    }
}
