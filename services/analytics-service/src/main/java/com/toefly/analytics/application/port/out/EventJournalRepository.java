package com.toefly.analytics.application.port.out;

import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.EventMeta;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import java.util.function.Consumer;

/** {@code event_journal}: every consumed event, decoded, kept 400 days for rebuilds (docs/04 §6.7). */
public interface EventJournalRepository {

    /** @return {@code false} when the event is already in the journal */
    boolean append(EventMeta meta, AnalyticsEvent event);

    /** Blocks new appends until the current transaction ends, so a rebuild sees a closed journal. */
    void lockForReplay();

    /** Feeds every event to {@code sink} in the order of {@code occurred_at}, then {@code event_id}. */
    long replay(Consumer<AnalyticsEvent> sink);

    void deleteUser(UUID userId);

    /** Creates the monthly partitions of {@code [from, to]} that do not exist yet. */
    void ensurePartitions(YearMonth from, YearMonth to);

    /** Drops events that happened before {@code before}. */
    void deleteBefore(Instant before);
}
