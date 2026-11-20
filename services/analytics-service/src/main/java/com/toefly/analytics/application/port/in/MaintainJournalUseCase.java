package com.toefly.analytics.application.port.in;

/** Monthly partitions of {@code event_journal} ahead of time and the 400-day retention (docs/03 §6.15). */
public interface MaintainJournalUseCase {

    /** Days the journal keeps events for rebuilds. */
    int RETENTION_DAYS = 400;

    void maintain();
}
