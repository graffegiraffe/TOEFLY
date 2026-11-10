package com.toefly.common.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.toefly.common.events.ApicurioEventPayloadSerializer;
import com.toefly.common.events.ToeflyEventsProperties;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.events.TopicNames;
import com.toefly.events.content.ItemSetHiddenV1;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.stereotype.Component;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * An Avro record whose listener fails is retried from {@code <topic>.retry} and dead-lettered to {@code <topic>.dlq}
 * with its original bytes, so the consumer of {@code .retry} reads it like the main topic (docs/04-architecture.md
 * §4.5).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AvroRetryTopicIT.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.application.name=retry-test",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
            "spring.kafka.consumer.value-deserializer=io.apicurio.registry.serde.avro.AvroKafkaDeserializer",
            "spring.kafka.consumer.properties.apicurio.registry.url=${toefly.events.registry-url}",
            "spring.kafka.consumer.properties.apicurio.registry.use-specific-avro-reader=true",
            "toefly.events.retry.delay=1s",
            "toefly.events.retry.attempts=2"
        })
class AvroRetryTopicIT {

    static final String RECOVERS = "toefly.test.retry-recovers.v1";
    static final String FAILS = "toefly.test.retry-fails.v1";

    @Autowired
    KafkaContainer kafka;

    @Autowired
    ToeflyEventsProperties properties;

    @Autowired
    Listeners listeners;

    @Test
    @DisplayName("a record that fails once is consumed again from .retry with the same Avro value")
    void failedOnceSucceedsFromRetry() {
        ItemSetHiddenV1 event = event();
        byte[] bytes = publish(RECOVERS, event);

        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(listeners.recovering).hasSize(2));
        assertThat(listeners.recovering)
                .extracting(Delivery::topic)
                .containsExactly(RECOVERS, TopicNames.retry(RECOVERS));
        assertThat(listeners.recovering).extracting(Delivery::value).containsOnly(event);
        assertThat(read(TopicNames.retry(RECOVERS), 1)).singleElement().isEqualTo(bytes);
    }

    @Test
    @DisplayName("a record that keeps failing ends in .dlq with its original bytes")
    void keepsFailingEndsInDlq() {
        ItemSetHiddenV1 event = event();
        byte[] bytes = publish(FAILS, event);

        List<byte[]> dead = read(TopicNames.dlq(FAILS), 1);

        assertThat(dead).singleElement().isEqualTo(bytes);
        assertThat(listeners.failing)
                .extracting(Delivery::topic)
                .containsExactly(FAILS, TopicNames.retry(FAILS), TopicNames.retry(FAILS));
        assertThat(listeners.failing).extracting(Delivery::value).containsOnly(event);
    }

    private static ItemSetHiddenV1 event() {
        return ItemSetHiddenV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .setItemSetId(UUID.randomUUID())
                .setReason("ADMIN")
                .build();
    }

    private byte[] publish(String topic, ItemSetHiddenV1 event) {
        byte[] bytes;
        try (var serializer = new ApicurioEventPayloadSerializer(properties)) {
            bytes = serializer.serialize(topic, event).bytes();
        }
        try (var producer = new KafkaProducer<>(
                Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringSerializer(),
                new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(topic, event.getItemSetId().toString(), bytes));
            producer.flush();
        }
        return bytes;
    }

    private List<byte[]> read(String topic, int count) {
        try (var consumer = new KafkaConsumer<>(
                Map.<String, Object>of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        kafka.getBootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG,
                        "reader-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        "earliest"),
                new StringDeserializer(),
                new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(topic));
            List<byte[]> values = new ArrayList<>();
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    values.add(record.value());
                }
                assertThat(values).hasSize(count);
            });
            return values;
        }
    }

    record Delivery(String topic, ItemSetHiddenV1 value) {}

    @Component
    static class Listeners {
        final List<Delivery> recovering = new CopyOnWriteArrayList<>();
        final List<Delivery> failing = new CopyOnWriteArrayList<>();

        @ToeflyListener(topics = RECOVERS)
        void recovers(ConsumerRecord<String, ItemSetHiddenV1> record) {
            recovering.add(new Delivery(record.topic(), record.value()));
            if (recovering.size() == 1) {
                throw new IllegalStateException("projection store is down");
            }
        }

        @ToeflyListener(topics = FAILS)
        void fails(ConsumerRecord<String, ItemSetHiddenV1> record) {
            failing.add(new Delivery(record.topic(), record.value()));
            throw new IllegalStateException("projection store is down");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
            excludeName = {
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
            })
    @Import({
        KafkaTestcontainersConfiguration.class,
        ApicurioTestcontainersConfiguration.class,
        Listeners.class,
        AvroRetryTopicIT.Topics.class
    })
    static class App {}

    static class Topics {
        @Bean
        NewTopic recovers() {
            return TopicBuilder.name(RECOVERS).partitions(1).build();
        }

        @Bean
        NewTopic recoversRetry() {
            return TopicBuilder.name(TopicNames.retry(RECOVERS)).partitions(1).build();
        }

        @Bean
        NewTopic recoversDlq() {
            return TopicBuilder.name(TopicNames.dlq(RECOVERS)).partitions(1).build();
        }

        @Bean
        NewTopic fails() {
            return TopicBuilder.name(FAILS).partitions(1).build();
        }

        @Bean
        NewTopic failsRetry() {
            return TopicBuilder.name(TopicNames.retry(FAILS)).partitions(1).build();
        }

        @Bean
        NewTopic failsDlq() {
            return TopicBuilder.name(TopicNames.dlq(FAILS)).partitions(1).build();
        }
    }
}
