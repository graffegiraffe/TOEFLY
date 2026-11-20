package com.toefly.analytics.adapter.out.persistence;

import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.EventMeta;
import com.toefly.analytics.application.port.out.EventJournalRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code event_journal}: the payload is the decoded event as JSON with its record name in {@code @type}; a rebuild reads
 * it back into the same record, so a replay applies exactly what the listener applied.
 */
@Repository
class JdbcEventJournalAdapter implements EventJournalRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcEventJournalAdapter.class);
    private static final String TYPE_FIELD = "@type";
    private static final int BATCH = 500;
    private static final Pattern PARTITION = Pattern.compile("event_journal_y(\\d{4})m(\\d{2})");
    private static final Map<String, Class<?>> EVENTS = Arrays.stream(AnalyticsEvent.class.getPermittedSubclasses())
            .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcEventJournalAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean append(EventMeta meta, AnalyticsEvent event) {
        ObjectNode payload = json.valueToTree(event);
        payload.put(TYPE_FIELD, event.getClass().getSimpleName());
        return jdbc.sql("""
                        INSERT INTO event_journal (event_id, type, user_id, occurred_at, payload)
                        VALUES (:id, :type, :user, :at, CAST(:payload AS jsonb))
                        ON CONFLICT DO NOTHING
                        """)
                        .param("id", meta.eventId())
                        .param("type", meta.type())
                        .param("user", meta.userId())
                        .param("at", Timestamp.from(meta.occurredAt()))
                        .param("payload", json.writeValueAsString(payload))
                        .update()
                == 1;
    }

    @Override
    public void lockForReplay() {
        jdbc.sql("LOCK TABLE event_journal IN SHARE MODE").update();
    }

    @Override
    public long replay(Consumer<AnalyticsEvent> sink) {
        long replayed = 0;
        Instant afterAt = Instant.EPOCH.minusSeconds(1);
        UUID afterId = new UUID(0, 0);
        while (true) {
            List<Row> rows = jdbc.sql("""
                            SELECT event_id, occurred_at, payload::text AS payload FROM event_journal
                            WHERE (occurred_at, event_id) > (:at, :id)
                            ORDER BY occurred_at, event_id
                            LIMIT :limit
                            """)
                    .param("at", Timestamp.from(afterAt))
                    .param("id", afterId)
                    .param("limit", BATCH)
                    .query((rs, n) -> new Row(
                            rs.getObject("event_id", UUID.class),
                            rs.getTimestamp("occurred_at").toInstant(),
                            rs.getString("payload")))
                    .list();
            for (Row row : rows) {
                sink.accept(decode(row.payload()));
                replayed++;
            }
            if (rows.size() < BATCH) {
                return replayed;
            }
            Row last = rows.getLast();
            afterAt = last.at();
            afterId = last.id();
        }
    }

    @Override
    public void deleteUser(UUID userId) {
        jdbc.sql("DELETE FROM event_journal WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    @Override
    public void ensurePartitions(YearMonth from, YearMonth to) {
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            String name = "event_journal_y%04dm%02d".formatted(month.getYear(), month.getMonthValue());
            Boolean exists = jdbc.sql("SELECT to_regclass(:name) IS NOT NULL")
                    .param("name", name)
                    .query(Boolean.class)
                    .single();
            if (Boolean.TRUE.equals(exists)) {
                continue;
            }
            String lower = bound(month);
            String upper = bound(month.plusMonths(1));
            // Rows of the month that landed in the default partition move into the new one before it is attached.
            jdbc.sql("CREATE TABLE " + name + " (LIKE event_journal INCLUDING DEFAULTS)")
                    .update();
            jdbc.sql("INSERT INTO " + name + " SELECT * FROM event_journal_default WHERE occurred_at >= '" + lower
                            + "' AND occurred_at < '" + upper + "'")
                    .update();
            jdbc.sql("DELETE FROM event_journal_default WHERE occurred_at >= '" + lower + "' AND occurred_at < '"
                            + upper + "'")
                    .update();
            jdbc.sql("ALTER TABLE event_journal ATTACH PARTITION " + name + " FOR VALUES FROM ('" + lower + "') TO ('"
                            + upper + "')")
                    .update();
            log.info("Created journal partition {}", name);
        }
    }

    @Override
    public void deleteBefore(Instant before) {
        List<String> partitions = jdbc.sql("""
                        SELECT c.relname FROM pg_inherits i
                        JOIN pg_class c ON c.oid = i.inhrelid
                        JOIN pg_class p ON p.oid = i.inhparent
                        WHERE p.relname = 'event_journal'
                        """).query(String.class).list();
        for (String partition : partitions) {
            Matcher matcher = PARTITION.matcher(partition);
            if (!matcher.matches()) {
                continue;
            }
            YearMonth month = YearMonth.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            Instant end = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            if (!end.isAfter(before)) {
                jdbc.sql("DROP TABLE " + partition).update();
                log.info("Dropped journal partition {} past the retention", partition);
            }
        }
        jdbc.sql("DELETE FROM event_journal_default WHERE occurred_at < :before")
                .param("before", Timestamp.from(before))
                .update();
    }

    private AnalyticsEvent decode(String payload) {
        ObjectNode node = (ObjectNode) json.readTree(payload);
        String type = node.remove(TYPE_FIELD).asString();
        Class<?> target = EVENTS.get(type);
        if (target == null) {
            throw new IllegalStateException("Unknown journal event " + type);
        }
        return (AnalyticsEvent) json.treeToValue(node, target);
    }

    private static String bound(YearMonth month) {
        return OffsetDateTime.of(month.atDay(1).atStartOfDay(), ZoneOffset.UTC).toString();
    }

    private record Row(UUID id, Instant at, String payload) {}
}
