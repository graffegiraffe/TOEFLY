package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.AttemptView;
import com.toefly.practice.application.port.in.Caller;
import com.toefly.practice.application.port.in.GetTimeSummaryUseCase;
import com.toefly.practice.application.port.in.RecordHeartbeatUseCase;
import com.toefly.practice.application.port.in.TimeBucket;
import com.toefly.practice.application.port.in.TimeGrouping;
import com.toefly.practice.application.port.out.AttemptStorePort;
import com.toefly.practice.application.port.out.AttemptStorePort.EventMetadata;
import com.toefly.practice.application.port.out.AttemptViewPort;
import com.toefly.practice.application.port.out.TimeEntryPort;
import com.toefly.practice.domain.attempt.Attempt;
import com.toefly.practice.domain.attempt.AttemptAlreadySubmittedException;
import com.toefly.practice.domain.attempt.AttemptMode;
import com.toefly.practice.domain.attempt.AttemptNotFoundException;
import com.toefly.practice.domain.attempt.AttemptStateConflictException;
import com.toefly.practice.domain.attempt.AttemptStatus;
import com.toefly.practice.domain.time.ContextType;
import com.toefly.practice.domain.time.HeartbeatState;
import com.toefly.practice.domain.time.TimeEntry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Active time of attempts and of study outside attempts (docs/03-domain-model.md §13, US-16.01, US-16.02). */
@Service
public class TimeTrackingService implements RecordHeartbeatUseCase, GetTimeSummaryUseCase {

    private final TimeEntryPort timeEntries;
    private final AttemptViewPort views;
    private final AttemptStorePort store;
    private final StudentAccess access;
    private final Transactions transactions;
    private final TraceContext trace;
    private final Clock clock;

    public TimeTrackingService(
            TimeEntryPort timeEntries,
            AttemptViewPort views,
            AttemptStorePort store,
            StudentAccess access,
            Transactions transactions,
            TraceContext trace,
            Clock clock) {
        this.timeEntries = timeEntries;
        this.views = views;
        this.store = store;
        this.access = access;
        this.transactions = transactions;
        this.trace = trace;
        this.clock = clock;
    }

    @Override
    public void attemptHeartbeat(AttemptHeartbeat heartbeat) {
        transactions.retryOnConflict(() -> {
            AttemptView view = views.find(heartbeat.attemptId())
                    .filter(found -> found.userId().equals(heartbeat.userId()))
                    .orElseThrow(() -> new AttemptNotFoundException(heartbeat.attemptId()));
            if (view.status() != AttemptStatus.IN_PROGRESS) {
                throw new AttemptAlreadySubmittedException(view.status());
            }
            Instant now = clock.instant();
            TimeEntry entry = timeEntries
                    .find(ContextType.ATTEMPT, view.attemptId(), view.userId())
                    .orElseGet(() -> TimeEntry.open(
                            view.userId(),
                            ContextType.ATTEMPT,
                            view.attemptId(),
                            view.taskType(),
                            view.taskType().weekSection(),
                            LocalDate.ofInstant(view.startedAt(), ZoneOffset.UTC),
                            view.startedAt(),
                            view.estimatedSeconds()));
            if (heartbeat.state() != HeartbeatState.ACTIVE) {
                pauseOrResume(view, heartbeat, now);
            }
            if (entry.heartbeat(
                    heartbeat.sequence(),
                    heartbeat.state(),
                    heartbeat.activeSecondsDelta(),
                    now,
                    heartbeat.audioReplays())) {
                timeEntries.save(entry);
                views.touch(view.attemptId(), now, entry.activeSeconds());
            }
            return Boolean.TRUE;
        });
    }

    /** PAUSED and RESUMED heartbeats are the pause button of Practice (AC-06.01.3); EXAM has none (INV-ATT-07). */
    private void pauseOrResume(AttemptView view, AttemptHeartbeat heartbeat, Instant now) {
        if (view.mode() == AttemptMode.EXAM) {
            throw new AttemptStateConflictException("An exam attempt cannot be paused.");
        }
        Attempt attempt =
                store.load(view.attemptId()).orElseThrow(() -> new AttemptNotFoundException(view.attemptId()));
        if (heartbeat.state() == HeartbeatState.PAUSED) {
            attempt.pause(now);
        } else {
            attempt.resume(now);
        }
        if (!attempt.pendingEvents().isEmpty()) {
            store.append(attempt, new EventMetadata(view.userId(), heartbeat.correlationId(), trace.traceparent()));
            views.upsert(AttemptProjector.project(attempt.snapshot(), view.activeSeconds(), now));
        }
    }

    @Override
    public void timeHeartbeat(TimeHeartbeat heartbeat) {
        transactions.retryOnConflict(() -> {
            Instant now = clock.instant();
            LocalDate day = LocalDate.ofInstant(now, ZoneOffset.UTC);
            UUID contextId = contextId(heartbeat, day);
            TimeEntry entry = timeEntries
                    .find(heartbeat.contextType(), contextId, heartbeat.userId())
                    .orElseGet(() -> TimeEntry.open(
                            heartbeat.userId(),
                            heartbeat.contextType(),
                            contextId,
                            null,
                            heartbeat.weekSection(),
                            day,
                            now.minusSeconds(heartbeat.activeSecondsDelta()),
                            0));
            if (entry.heartbeat(
                    heartbeat.sequence(), HeartbeatState.ACTIVE, heartbeat.activeSecondsDelta(), now, null)) {
                timeEntries.save(entry);
            }
            return Boolean.TRUE;
        });
    }

    /** One entry per context object and day: a week task, a unit or a material. */
    private static UUID contextId(TimeHeartbeat heartbeat, LocalDate day) {
        String key = heartbeat.contextType() + "|" + heartbeat.weekTaskId() + "|" + heartbeat.refId() + "|" + day;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeBucket> summary(
            Caller caller, @Nullable UUID studentId, LocalDate from, LocalDate to, TimeGrouping grouping) {
        return timeEntries.summary(access.target(caller, studentId), from, to, grouping);
    }
}
