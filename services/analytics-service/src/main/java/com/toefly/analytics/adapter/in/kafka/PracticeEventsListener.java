package com.toefly.analytics.adapter.in.kafka;

import com.toefly.analytics.application.port.in.RecordEventUseCase;
import com.toefly.analytics.application.port.in.event.AbilityUpdated;
import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.AttemptScored;
import com.toefly.analytics.application.port.in.event.AttemptStarted;
import com.toefly.analytics.application.port.in.event.AttemptSubmitted;
import com.toefly.analytics.application.port.in.event.EventMeta;
import com.toefly.analytics.application.port.in.event.TimeTracked;
import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.common.TaskType;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.practice.AbilityUpdatedV1;
import com.toefly.events.practice.AttemptScoredV1;
import com.toefly.events.practice.AttemptStartedV1;
import com.toefly.events.practice.AttemptSubmittedV1;
import com.toefly.events.practice.TimeTrackedV1;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** Attempts, θ and active time: the results of the dashboard, the activity days and the pace against ETS norms. */
@Component
public class PracticeEventsListener {

    private final ProcessedEventGuard guard;
    private final RecordEventUseCase recorder;
    private final ConsistencyLag lag;

    public PracticeEventsListener(ProcessedEventGuard guard, RecordEventUseCase recorder, ConsistencyLag lag) {
        this.guard = guard;
        this.recorder = recorder;
        this.lag = lag;
    }

    @ToeflyListener(id = "attempt-started", topics = "toefly.practice.attempt-started.v1")
    void onStarted(ConsumerRecord<String, AttemptStartedV1> record) {
        AttemptStartedV1 e = record.value();
        TaskType type = Enums.taskType(e.getTaskType());
        if (type == null) {
            return;
        }
        apply(
                record,
                "PracticeEventsListener.started",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new AttemptStarted(e.getUserId(), e.getAttemptId(), type, e.getEstimatedSeconds(), e.getOccurredAt()));
    }

    @ToeflyListener(id = "attempt-submitted", topics = "toefly.practice.attempt-submitted.v1")
    void onSubmitted(ConsumerRecord<String, AttemptSubmittedV1> record) {
        AttemptSubmittedV1 e = record.value();
        apply(
                record,
                "PracticeEventsListener.submitted",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new AttemptSubmitted(e.getUserId(), e.getAttemptId(), e.getOccurredAt()));
    }

    @ToeflyListener(id = "attempt-scored", topics = "toefly.practice.attempt-scored.v1")
    void onScored(ConsumerRecord<String, AttemptScoredV1> record) {
        AttemptScoredV1 e = record.value();
        TaskType type = Enums.taskType(e.getTaskType());
        if (type == null) {
            return;
        }
        apply(
                record,
                "PracticeEventsListener.scored",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new AttemptScored(
                        e.getUserId(),
                        e.getAttemptId(),
                        type,
                        Enums.section(e.getSection()),
                        e.getCorrectCount(),
                        e.getTotalCount(),
                        e.getOccurredAt()));
    }

    @ToeflyListener(id = "ability-updated", topics = "toefly.practice.ability-updated.v1")
    void onAbility(ConsumerRecord<String, AbilityUpdatedV1> record) {
        AbilityUpdatedV1 e = record.value();
        Section section = Enums.section(e.getSection());
        if (section == null) {
            return;
        }
        apply(
                record,
                "PracticeEventsListener.ability",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new AbilityUpdated(e.getUserId(), section, e.getThetaAfter(), e.getStandardError(), e.getOccurredAt()));
    }

    @ToeflyListener(id = "time-tracked", topics = "toefly.practice.time-tracked.v1")
    void onTime(ConsumerRecord<String, TimeTrackedV1> record) {
        TimeTrackedV1 e = record.value();
        TaskType type = Enums.taskType(e.getTaskType());
        if (type == null) {
            return;
        }
        apply(
                record,
                "PracticeEventsListener.time",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new TimeTracked(
                        e.getUserId(),
                        e.getAttemptId(),
                        type,
                        Enums.weekSection(e.getWeekSection()),
                        e.getLocalDate(),
                        e.getActiveSeconds(),
                        e.getSuspicious(),
                        e.getOccurredAt()));
    }

    private void apply(
            ConsumerRecord<?, ?> record, String consumer, UUID eventId, UUID userId, Instant at, AnalyticsEvent event) {
        EventMeta meta = new EventMeta(eventId, record.topic(), at, userId);
        lag.applied(guard.runOnce(record, consumer, () -> recorder.record(meta, event)), at);
    }
}
