package com.toefly.common.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.toefly.common.events.CloudEventAttributes;
import com.toefly.common.events.EventPayloadSerializer.SerializedPayload;
import com.toefly.common.observability.TraceparentProvider;
import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.JdbcOutboxWriter;
import com.toefly.common.outbox.OutboxIntegrationEventPublisher;
import com.toefly.common.outbox.ProcessedEventGuard;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Phase 0 acceptance: a row written through the outbox reaches Kafka via Debezium with CloudEvents headers and the
 * trace context, and a redelivered record does not repeat the consumer's effect.
 */
@Testcontainers(disabledWithoutDocker = true)
class OutboxDebeziumRoundTripIT {

    private static final String TOPIC = "toefly.demo.item-created.v1";
    private static final Network NETWORK = Network.newNetwork();
    private static final PostgreSQLContainer POSTGRES = PostgresTestcontainersConfiguration.newPostgres()
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres");
    private static final KafkaContainer KAFKA =
            new KafkaContainer(ToeflyTestImages.KAFKA).withNetwork(NETWORK).withListener("kafka:19092");
    private static final GenericContainer<?> CONNECT = new GenericContainer<>(ToeflyTestImages.DEBEZIUM_CONNECT)
            .withNetwork(NETWORK)
            .withEnv("BOOTSTRAP_SERVERS", "kafka:19092")
            .withEnv("GROUP_ID", "toefly-connect")
            .withEnv("CONFIG_STORAGE_TOPIC", "toefly-connect-configs")
            .withEnv("OFFSET_STORAGE_TOPIC", "toefly-connect-offsets")
            .withEnv("STATUS_STORAGE_TOPIC", "toefly-connect-status")
            .withExposedPorts(8083)
            .waitingFor(Wait.forHttp("/connectors").forPort(8083).withStartupTimeout(Duration.ofMinutes(3)));

    private static JdbcClient jdbc;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        POSTGRES.start();
        KAFKA.start();
        CONNECT.dependsOn(KAFKA, POSTGRES).start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/common")
                .load()
                .migrate();
        var dataSource =
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("CREATE TABLE demo_effect (event_id uuid NOT NULL)").update();
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        registerConnector();
    }

    @AfterAll
    static void stopInfrastructure() {
        CONNECT.stop();
        KAFKA.stop();
        POSTGRES.stop();
        NETWORK.close();
    }

    @Test
    void eventTravelsFromOutboxToConsumerExactlyOnce() {
        SimpleTracer tracer = new SimpleTracer();
        var publisher = new OutboxIntegrationEventPublisher(
                new JdbcOutboxWriter(jdbc),
                (topic, record) ->
                        new SerializedPayload(new byte[] {0, 1, 2}, "apicurio://toefly/" + topic + "-value/1"),
                new TraceparentProvider(tracer),
                "demo-service");
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var span = tracer.nextSpan().name("POST /api/v1/items").start();
        try (var scope = tracer.withSpan(span)) {
            transactions.executeWithoutResult(status -> publisher.publish(new IntegrationEvent(
                    eventId,
                    TOPIC,
                    "Item",
                    "42",
                    userId.toString(),
                    "items/42",
                    Instant.parse("2026-11-01T09:30:00Z"),
                    userId,
                    null,
                    null,
                    emptyRecord())));
        } finally {
            span.end();
        }

        ConsumerRecord<String, byte[]> record = consumeOne();
        CloudEventAttributes attributes = CloudEventAttributes.fromHeaders(record.headers());
        assertThat(record.key()).isEqualTo(userId.toString());
        assertThat(record.value()).containsExactly(0, 1, 2);
        assertThat(attributes.id()).isEqualTo(eventId);
        assertThat(attributes.type()).isEqualTo(TOPIC);
        assertThat(attributes.source()).isEqualTo("/toefly/demo-service");
        assertThat(attributes.subject()).isEqualTo("items/42");
        assertThat(attributes.traceparent()).contains(span.context().traceId());
        assertThat(new String(record.headers().lastHeader("ce_specversion").value(), StandardCharsets.UTF_8))
                .isEqualTo("1.0");

        var guard = new ProcessedEventGuard(jdbc, transactions);
        Runnable effect = () -> jdbc.sql("INSERT INTO demo_effect VALUES (:id)")
                .param("id", eventId)
                .update();
        assertThat(guard.runOnce(record, "DemoItemCreatedListener", effect)).isTrue();
        assertThat(guard.runOnce(record, "DemoItemCreatedListener", effect)).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM demo_effect")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    private static ConsumerRecord<String, byte[]> consumeOne() {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "demo-service",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(TOPIC));
            var holder = new Object() {
                ConsumerRecord<String, byte[]> record;
            };
            await().atMost(Duration.ofMinutes(1)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> holder.record = record);
                return holder.record != null;
            });
            return holder.record;
        }
    }

    private static void registerConnector() throws Exception {
        String connector = """
                {"name": "outbox-demo-service", "config": {
                  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
                  "tasks.max": "1",
                  "database.hostname": "postgres", "database.port": "5432",
                  "database.user": "%s", "database.password": "%s", "database.dbname": "%s",
                  "topic.prefix": "outbox.demo", "plugin.name": "pgoutput",
                  "publication.name": "dbz_outbox", "publication.autocreate.mode": "disabled",
                  "slot.name": "dbz_demo", "table.include.list": "public.outbox_event",
                  "tombstones.on.delete": "false", "snapshot.mode": "no_data",
                  "transforms": "outbox,ceContentType",
                  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
                  "transforms.outbox.route.by.field": "topic",
                  "transforms.outbox.route.topic.replacement": "${routedByValue}",
                  "transforms.outbox.table.field.event.id": "id",
                  "transforms.outbox.table.field.event.key": "partition_key",
                  "transforms.outbox.table.field.event.payload": "payload",
                  "transforms.outbox.table.fields.additional.placement": "id:header:ce_id,event_type:header:ce_type,source:header:ce_source,subject:header:ce_subject,occurred_at:header:ce_time,data_schema:header:ce_dataschema,trace_parent:header:traceparent,correlation_id:header:ce_correlationid,causation_id:header:ce_causationid,partition_key:header:ce_partitionkey,spec_version:header:ce_specversion",
                  "transforms.ceContentType.type": "org.apache.kafka.connect.transforms.InsertHeader",
                  "transforms.ceContentType.header": "content-type",
                  "transforms.ceContentType.value.literal": "application/avro",
                  "key.converter": "org.apache.kafka.connect.storage.StringConverter",
                  "value.converter": "org.apache.kafka.connect.converters.ByteArrayConverter",
                  "header.converter": "org.apache.kafka.connect.storage.StringConverter"
                }}
                """.formatted(POSTGRES.getUsername(), POSTGRES.getPassword(), POSTGRES.getDatabaseName());
        String base = "http://" + CONNECT.getHost() + ":" + CONNECT.getMappedPort(8083) + "/connectors";
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> created = http.send(
                HttpRequest.newBuilder(URI.create(base))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(connector))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        await().atMost(Duration.ofMinutes(1))
                .until(() -> http.send(
                                HttpRequest.newBuilder(URI.create(base + "/outbox-demo-service/status"))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .body()
                        .matches("(?s).*\"tasks\":\\[\\{[^]]*\"state\":\"RUNNING\".*"));
    }

    private static SpecificRecord emptyRecord() {
        class Empty extends GenericData.Record implements SpecificRecord {
            Empty() {
                super(SchemaBuilder.record("Empty").fields().endRecord());
            }
        }
        return new Empty();
    }
}
