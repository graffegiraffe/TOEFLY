package com.toefly.practice.adapter.out.persistence;

import com.toefly.practice.application.port.out.AttemptStorePort;
import com.toefly.practice.application.port.out.ConcurrencyConflictException;
import com.toefly.practice.domain.attempt.Attempt;
import com.toefly.practice.domain.attempt.AttemptEvent;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.attempt.AttemptStatus;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Event store on PostgreSQL (ADR-0010). Appends only; the unique key (stream_id, version) is the optimistic lock.
 * A snapshot is written every 20 events and when the attempt is scored.
 */
@Repository
public class JdbcAttemptStoreAdapter implements AttemptStorePort {

    static final int SNAPSHOT_EVERY = 20;
    static final int SNAPSHOT_SCHEMA = 1;
    private static final int REBUILD_BATCH = 1000;

    private final JdbcClient jdbc;

    public JdbcAttemptStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Attempt> load(UUID attemptId) {
        Optional<AttemptSnapshot> snapshot = jdbc.sql(
                        "SELECT state::text FROM attempt_snapshot WHERE stream_id = :id AND schema_version = :schema")
                .param("id", attemptId)
                .param("schema", SNAPSHOT_SCHEMA)
                .query(String.class)
                .optional()
                .map(json -> EventJson.MAPPER.readValue(json, AttemptSnapshot.class));
        int after = snapshot.map(AttemptSnapshot::version).orElse(0);
        List<AttemptEvent> tail = jdbc.sql("""
                        SELECT event_type, payload::text FROM attempt_event
                        WHERE stream_id = :id AND version > :after ORDER BY version
                        """)
                .param("id", attemptId)
                .param("after", after)
                .query((rs, row) -> EventJson.read(rs.getString(1), rs.getString(2)))
                .list();
        if (snapshot.isPresent()) {
            return Optional.of(Attempt.restore(snapshot.get(), tail));
        }
        return tail.isEmpty() ? Optional.empty() : Optional.of(Attempt.replay(tail));
    }

    @Override
    public void append(Attempt attempt, EventMetadata metadata) {
        List<AttemptEvent> events = attempt.pendingEvents();
        if (events.isEmpty()) {
            return;
        }
        int version = attempt.persistedVersion();
        String meta = EventJson.MAPPER.writeValueAsString(Map.of(
                "userId", metadata.userId().toString(),
                "correlationId", metadata.correlationId().toString(),
                "traceparent", metadata.traceparent()));
        try {
            for (AttemptEvent event : events) {
                version++;
                jdbc.sql("""
                                INSERT INTO attempt_event
                                    (stream_id, version, event_type, payload, metadata, occurred_at, user_id)
                                VALUES (:stream, :version, :type, :payload::jsonb, :metadata::jsonb, :at, :user)
                                """)
                        .param("stream", attempt.id())
                        .param("version", version)
                        .param("type", EventJson.type(event))
                        .param("payload", EventJson.write(event))
                        .param("metadata", meta)
                        .param("at", Timestamp.from(event.occurredAt()))
                        .param("user", attempt.userId())
                        .update();
            }
        } catch (DuplicateKeyException conflict) {
            throw new ConcurrencyConflictException(attempt.id(), attempt.persistedVersion());
        }
        boolean snapshotDue = attempt.status() == AttemptStatus.SCORED
                || attempt.version() / SNAPSHOT_EVERY > attempt.persistedVersion() / SNAPSHOT_EVERY;
        attempt.markPersisted();
        if (snapshotDue) {
            jdbc.sql("""
                            INSERT INTO attempt_snapshot (stream_id, version, state, schema_version)
                            VALUES (:stream, :version, :state::jsonb, :schema)
                            ON CONFLICT (stream_id) DO UPDATE
                            SET version = EXCLUDED.version, state = EXCLUDED.state,
                                schema_version = EXCLUDED.schema_version, created_at = now()
                            """)
                    .param("stream", attempt.id())
                    .param("version", attempt.version())
                    .param("state", EventJson.MAPPER.writeValueAsString(attempt.snapshot()))
                    .param("schema", SNAPSHOT_SCHEMA)
                    .update();
        }
    }

    @Override
    public long forEachStream(Consumer<List<AttemptEvent>> consumer) {
        long position = 0;
        long events = 0;
        Map<UUID, List<AttemptEvent>> streams = new LinkedHashMap<>();
        while (true) {
            List<Row> rows = jdbc.sql("""
                            SELECT global_position, stream_id, event_type, payload::text FROM attempt_event
                            WHERE global_position > :after ORDER BY global_position LIMIT :limit
                            """)
                    .param("after", position)
                    .param("limit", REBUILD_BATCH)
                    .query((rs, row) -> new Row(
                            rs.getLong(1),
                            rs.getObject(2, UUID.class),
                            EventJson.read(rs.getString(3), rs.getString(4))))
                    .list();
            if (rows.isEmpty()) {
                break;
            }
            for (Row row : rows) {
                streams.computeIfAbsent(row.stream(), id -> new ArrayList<>()).add(row.event());
                position = row.position();
                events++;
            }
        }
        streams.values().forEach(consumer);
        return events;
    }

    /** The only DELETE of the event store: erasure of a user's streams (NFR-DUR-07). */
    @Override
    public int eraseUser(UUID userId) {
        jdbc.sql("DELETE FROM attempt_snapshot WHERE stream_id IN (SELECT DISTINCT stream_id FROM attempt_event"
                        + " WHERE user_id = :user)")
                .param("user", userId)
                .update();
        return jdbc.sql("DELETE FROM attempt_event WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    private record Row(long position, UUID stream, AttemptEvent event) {}
}
