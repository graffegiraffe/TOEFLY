package com.toefly.analytics.adapter.in.kafka;

import com.toefly.analytics.application.port.in.RecordEventUseCase;
import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.EventMeta;
import com.toefly.analytics.application.port.in.event.StudyPlanCreated;
import com.toefly.analytics.application.port.in.event.WeekCompleted;
import com.toefly.analytics.application.port.in.event.WeekPublished;
import com.toefly.analytics.application.port.in.event.WeekRebalanced;
import com.toefly.analytics.application.port.in.event.WeekTaskCompleted;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.planning.StudyPlanCreatedV1;
import com.toefly.events.planning.WeekCompletedV1;
import com.toefly.events.planning.WeekPublishedV1;
import com.toefly.events.planning.WeekRebalancedV1;
import com.toefly.events.planning.WeekTaskCompletedV1;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** The plan and its weeks: the current week, the weeks chart, the plan of each day and the funnel. */
@Component
public class PlanningEventsListener {

    private final ProcessedEventGuard guard;
    private final RecordEventUseCase recorder;
    private final ConsistencyLag lag;

    public PlanningEventsListener(ProcessedEventGuard guard, RecordEventUseCase recorder, ConsistencyLag lag) {
        this.guard = guard;
        this.recorder = recorder;
        this.lag = lag;
    }

    @ToeflyListener(id = "study-plan-created", topics = "toefly.planning.study-plan-created.v1")
    void onPlan(ConsumerRecord<String, StudyPlanCreatedV1> record) {
        StudyPlanCreatedV1 e = record.value();
        apply(
                record,
                "PlanningEventsListener.plan",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new StudyPlanCreated(
                        e.getUserId(),
                        e.getPlanId(),
                        e.getExamDate(),
                        e.getMinutesPerDay(),
                        e.getTimezone(),
                        e.getOccurredAt()));
    }

    @ToeflyListener(id = "week-published", topics = "toefly.planning.week-published.v1")
    void onPublished(ConsumerRecord<String, WeekPublishedV1> record) {
        WeekPublishedV1 e = record.value();
        apply(
                record,
                "PlanningEventsListener.published",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new WeekPublished(
                        e.getUserId(),
                        e.getWeekId(),
                        e.getWeekNo(),
                        e.getDay1(),
                        e.getTasks().stream()
                                .map(t -> new WeekPublished.TaskDay(t.getWeekTaskId(), t.getLocalDate()))
                                .toList(),
                        e.getOccurredAt()));
    }

    @ToeflyListener(id = "week-task-completed", topics = "toefly.planning.week-task-completed.v1")
    void onTaskCompleted(ConsumerRecord<String, WeekTaskCompletedV1> record) {
        WeekTaskCompletedV1 e = record.value();
        apply(
                record,
                "PlanningEventsListener.taskCompleted",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new WeekTaskCompleted(
                        e.getUserId(),
                        e.getWeekId(),
                        e.getWeekTaskId(),
                        e.getWeekNo(),
                        e.getLocalDate(),
                        e.getProgressPercent(),
                        e.getOccurredAt()));
    }

    @ToeflyListener(id = "week-rebalanced", topics = "toefly.planning.week-rebalanced.v1")
    void onRebalanced(ConsumerRecord<String, WeekRebalancedV1> record) {
        WeekRebalancedV1 e = record.value();
        apply(
                record,
                "PlanningEventsListener.rebalanced",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new WeekRebalanced(
                        e.getUserId(),
                        e.getWeekId(),
                        e.getRedistributedTo().stream()
                                .map(t -> new WeekPublished.TaskDay(t.getWeekTaskId(), t.getLocalDate()))
                                .toList(),
                        e.getOccurredAt()));
    }

    @ToeflyListener(id = "week-completed", topics = "toefly.planning.week-completed.v1")
    void onCompleted(ConsumerRecord<String, WeekCompletedV1> record) {
        WeekCompletedV1 e = record.value();
        apply(
                record,
                "PlanningEventsListener.completed",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new WeekCompleted(
                        e.getUserId(),
                        e.getWeekId(),
                        e.getWeekNo(),
                        e.getStartDate(),
                        e.getEndDate(),
                        e.getProgressPercent(),
                        e.getTasksTotal(),
                        e.getTasksDone(),
                        e.getClosedBy().name(),
                        e.getOccurredAt()));
    }

    private void apply(
            ConsumerRecord<?, ?> record, String consumer, UUID eventId, UUID userId, Instant at, AnalyticsEvent event) {
        EventMeta meta = new EventMeta(eventId, record.topic(), at, userId);
        lag.applied(guard.runOnce(record, consumer, () -> recorder.record(meta, event)), at);
    }
}
