package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.AttemptView;
import com.toefly.practice.application.port.in.RebuildProjectionUseCase;
import com.toefly.practice.application.port.out.AttemptStorePort;
import com.toefly.practice.application.port.out.AttemptViewPort;
import com.toefly.practice.application.port.out.TimeEntryPort;
import com.toefly.practice.domain.attempt.Attempt;
import com.toefly.practice.domain.attempt.AttemptEvent;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.time.ContextType;
import com.toefly.practice.domain.time.TimeEntry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rebuilds attempt_view from the streams with the same projector as the inline projection (ADR-0010). */
@Service
public class ProjectionRebuildService implements RebuildProjectionUseCase {

    private final AttemptStorePort store;
    private final AttemptViewPort views;
    private final TimeEntryPort timeEntries;
    private final Clock clock;

    public ProjectionRebuildService(
            AttemptStorePort store, AttemptViewPort views, TimeEntryPort timeEntries, Clock clock) {
        this.store = store;
        this.views = views;
        this.timeEntries = timeEntries;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Rebuild rebuild() {
        Instant started = clock.instant();
        AtomicLong events = new AtomicLong();
        List<AttemptView> rebuilt = new ArrayList<>();
        store.forEachStream(stream -> {
            events.addAndGet(stream.size());
            rebuilt.add(project(stream));
        });
        views.replaceAll(rebuilt);
        return new Rebuild(UUID.randomUUID(), started, clock.instant(), events.get());
    }

    AttemptView project(List<AttemptEvent> stream) {
        AttemptSnapshot state = Attempt.replay(stream).snapshot();
        int active = timeEntries
                .find(ContextType.ATTEMPT, state.attemptId(), state.start().userId())
                .map(TimeEntry::activeSeconds)
                .orElse(0);
        Instant lastActivity = stream.getLast().occurredAt();
        return AttemptProjector.project(state, active, lastActivity);
    }
}
