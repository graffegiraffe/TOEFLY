package com.toefly.planning.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.common.AttemptMode;
import com.toefly.events.practice.AbilityUpdatedV1;
import com.toefly.events.practice.AttemptScoredV1;
import com.toefly.events.practice.AttemptStartedV1;
import com.toefly.planning.application.port.in.SyncLearnerUseCase;
import com.toefly.planning.application.port.in.TrackAttemptUseCase;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** Attempts close the tasks of the week; θ feeds the choice of sets and weak types. */
@Component
public class PracticeEventsListener {

    private final ProcessedEventGuard guard;
    private final TrackAttemptUseCase attempts;
    private final SyncLearnerUseCase sync;

    public PracticeEventsListener(ProcessedEventGuard guard, TrackAttemptUseCase attempts, SyncLearnerUseCase sync) {
        this.guard = guard;
        this.attempts = attempts;
        this.sync = sync;
    }

    @ToeflyListener(id = "attempt-started", topics = "toefly.practice.attempt-started.v1")
    void onStarted(ConsumerRecord<String, AttemptStartedV1> record) {
        AttemptStartedV1 event = record.value();
        if (event.getWeekTaskId() == null) {
            return;
        }
        guard.runOnce(
                record,
                "PracticeEventsListener.started",
                () -> attempts.attemptStarted(
                        event.getUserId(),
                        event.getWeekTaskId(),
                        event.getAttemptId(),
                        event.getRetry(),
                        event.getStartedAt()));
    }

    @ToeflyListener(id = "attempt-scored", topics = "toefly.practice.attempt-scored.v1")
    void onScored(ConsumerRecord<String, AttemptScoredV1> record) {
        AttemptScoredV1 event = record.value();
        if (event.getTaskType() == com.toefly.events.common.TaskType.UNKNOWN) {
            return;
        }
        guard.runOnce(
                record,
                "PracticeEventsListener.scored",
                () -> attempts.attemptScored(
                        event.getUserId(),
                        event.getWeekTaskId(),
                        event.getAttemptId(),
                        TaskType.valueOf(event.getTaskType().name()),
                        event.getCorrectCount(),
                        event.getTotalCount(),
                        event.getMode() == AttemptMode.PRACTICE,
                        event.getScoredAt(),
                        new Cause(event.getCorrelationId(), event.getEventId())));
    }

    @ToeflyListener(id = "ability-updated", topics = "toefly.practice.ability-updated.v1")
    void onAbility(ConsumerRecord<String, AbilityUpdatedV1> record) {
        AbilityUpdatedV1 event = record.value();
        if (event.getSection() == com.toefly.events.common.Section.UNKNOWN) {
            return;
        }
        guard.runOnce(
                record,
                "PracticeEventsListener.ability",
                () -> sync.abilityUpdated(
                        event.getUserId(),
                        WeekSection.valueOf(event.getSection().name()),
                        event.getThetaAfter(),
                        event.getOccurredAt()));
    }
}
