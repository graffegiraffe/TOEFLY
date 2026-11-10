package com.toefly.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.stereotype.Component;

/**
 * A record the value deserializer cannot read goes to {@code <topic>.dlq} and the consumer moves on, instead of
 * failing on the same offset forever (docs/04-architecture.md §4.5).
 */
@SpringBootTest(
        classes = UnreadableRecordTest.App.class,
        properties = {
            "spring.application.name=events-test",
            "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
            "spring.kafka.consumer.value-deserializer=com.toefly.common.events.UnreadableRecordTest$StrictDeserializer",
            "toefly.events.retry.delay=100ms"
        })
@EmbeddedKafka(
        partitions = 1,
        topics = {UnreadableRecordTest.TOPIC, UnreadableRecordTest.TOPIC + ".retry", UnreadableRecordTest.TOPIC + ".dlq"
        })
class UnreadableRecordTest {

    static final String TOPIC = "toefly.test.thing-happened.v1";

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    Listener listener;

    @Test
    @DisplayName("an unreadable record goes to the DLQ with its bytes and the next record is consumed")
    void unreadableRecordGoesToTheDlq() {
        try (var producer = new KafkaProducer<>(
                Map.<String, Object>of("bootstrap.servers", broker.getBrokersAsString()),
                new StringSerializer(),
                new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(TOPIC, "k", bytes("poison")));
            producer.send(new ProducerRecord<>(TOPIC, "k", bytes("fine")));
            producer.flush();
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(listener.received).containsExactly("fine"));

        try (var consumer = new KafkaConsumer<>(
                Map.<String, Object>of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                        ConsumerConfig.GROUP_ID_CONFIG, "dlq-reader",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(),
                new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(TOPIC + ".dlq"));
            List<String> dead = new CopyOnWriteArrayList<>();
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    dead.add(new String(record.value(), StandardCharsets.UTF_8));
                }
                assertThat(dead).containsExactly("poison");
            });
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Reads only the word “fine”, like an Avro deserializer that meets an unknown schema id. */
    public static class StrictDeserializer implements Deserializer<String> {
        @Override
        public String deserialize(String topic, byte[] data) {
            String text = new String(data, StandardCharsets.UTF_8);
            if (!text.equals("fine")) {
                throw new IllegalStateException("cannot read " + text);
            }
            return text;
        }
    }

    @Component
    static class Listener {
        final List<String> received = new CopyOnWriteArrayList<>();

        @ToeflyListener(topics = TOPIC)
        void on(ConsumerRecord<String, String> record) {
            received.add(record.value());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(Listener.class)
    static class App {}
}
