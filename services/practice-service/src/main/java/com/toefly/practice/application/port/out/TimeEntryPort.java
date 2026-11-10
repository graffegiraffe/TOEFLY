package com.toefly.practice.application.port.out;

import com.toefly.practice.application.port.in.TimeBucket;
import com.toefly.practice.application.port.in.TimeGrouping;
import com.toefly.practice.domain.time.ContextType;
import com.toefly.practice.domain.time.TimeEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeEntryPort {

    Optional<TimeEntry> find(ContextType contextType, UUID contextId, UUID userId);

    /** Inserts or updates the entry at its version; a stale version is a concurrency conflict. */
    void save(TimeEntry entry);

    /** Active and estimated seconds per bucket in [from, to) of the learner's calendar. */
    List<TimeBucket> summary(UUID userId, LocalDate from, LocalDate to, TimeGrouping grouping);

    List<TimeEntry> findAll(UUID userId);

    int eraseUser(UUID userId);
}
