package com.toefly.common.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Checks of what a use case wrote to {@code outbox_event} in a component test. */
public final class OutboxAssertions {

    private OutboxAssertions() {}

    public record PublishedEvent(UUID id, String topic, String partitionKey, String source, String subject) {}

    public static List<PublishedEvent> published(JdbcClient jdbc, String topic) {
        return jdbc.sql("""
                        SELECT id, topic, partition_key, source, subject FROM outbox_event
                        WHERE topic = :topic ORDER BY created_at, id
                        """)
                .param("topic", topic)
                .query((rs, row) -> new PublishedEvent(
                        rs.getObject("id", UUID.class),
                        rs.getString("topic"),
                        rs.getString("partition_key"),
                        rs.getString("source"),
                        rs.getString("subject")))
                .list();
    }

    public static PublishedEvent assertPublishedOnce(JdbcClient jdbc, String topic) {
        List<PublishedEvent> events = published(jdbc, topic);
        assertThat(events).as("events in outbox for %s", topic).hasSize(1);
        return events.getFirst();
    }

    public static void assertNothingPublished(JdbcClient jdbc) {
        Long count =
                jdbc.sql("SELECT count(*) FROM outbox_event").query(Long.class).single();
        assertThat(count).as("rows in outbox_event").isZero();
    }
}
