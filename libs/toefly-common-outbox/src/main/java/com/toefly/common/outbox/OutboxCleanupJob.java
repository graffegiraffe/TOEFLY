package com.toefly.common.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

/** Removes published outbox rows after 72 hours, processed events after 30 days and expired idempotency records. */
public class OutboxCleanupJob {

    static final Duration OUTBOX_RETENTION = Duration.ofHours(72);
    static final Duration PROCESSED_EVENTS_RETENTION = Duration.ofDays(30);

    private final JdbcClient jdbc;
    private final Clock clock;

    public OutboxCleanupJob(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "${toefly.outbox.cleanup-cron:0 17 * * * *}")
    public void cleanUp() {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.sql("DELETE FROM outbox_event WHERE created_at < :before")
                .param("before", Timestamp.from(clock.instant().minus(OUTBOX_RETENTION)))
                .update();
        jdbc.sql("DELETE FROM processed_events WHERE processed_at < :before")
                .param("before", Timestamp.from(clock.instant().minus(PROCESSED_EVENTS_RETENTION)))
                .update();
        jdbc.sql("DELETE FROM idempotency_record WHERE expires_at < :now")
                .param("now", now)
                .update();
    }
}
