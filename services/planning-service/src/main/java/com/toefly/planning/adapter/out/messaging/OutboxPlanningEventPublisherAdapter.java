package com.toefly.planning.adapter.out.messaging;

import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.common.WeekCloseReason;
import com.toefly.events.common.WeekStatus;
import com.toefly.events.planning.PublishedWeekTaskV1;
import com.toefly.events.planning.RedistributedTaskV1;
import com.toefly.events.planning.SectionProgressV1;
import com.toefly.events.planning.StudyPlanCreatedV1;
import com.toefly.events.planning.WeekCompletedV1;
import com.toefly.events.planning.WeekDraftGeneratedV1;
import com.toefly.events.planning.WeekPublishedV1;
import com.toefly.events.planning.WeekRebalancedV1;
import com.toefly.events.planning.WeekTaskCompletedV1;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.PlanningEventPublisherPort;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Rebalance;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Maps planning facts to the Avro events of the canon and writes them to the outbox (ADR-0005). */
@Component
public class OutboxPlanningEventPublisherAdapter implements PlanningEventPublisherPort {

    static final String STUDY_PLAN_CREATED = "toefly.planning.study-plan-created.v1";
    static final String WEEK_DRAFT_GENERATED = "toefly.planning.week-draft-generated.v1";
    static final String WEEK_PUBLISHED = "toefly.planning.week-published.v1";
    static final String WEEK_TASK_COMPLETED = "toefly.planning.week-task-completed.v1";
    static final String WEEK_REBALANCED = "toefly.planning.week-rebalanced.v1";
    static final String WEEK_COMPLETED = "toefly.planning.week-completed.v1";

    private final IntegrationEventPublisher publisher;
    private final Clock clock;

    public OutboxPlanningEventPublisherAdapter(IntegrationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public List<String> topics() {
        return List.of(
                STUDY_PLAN_CREATED,
                WEEK_DRAFT_GENERATED,
                WEEK_PUBLISHED,
                WEEK_TASK_COMPLETED,
                WEEK_REBALANCED,
                WEEK_COMPLETED);
    }

    @Override
    public void studyPlanCreated(StudyPlan plan, Cause cause) {
        UUID eventId = UUID.randomUUID();
        Instant now = plan.createdAt();
        publish(
                eventId,
                STUDY_PLAN_CREATED,
                "StudyPlan",
                plan.id(),
                plan.userId(),
                "study-plans/" + plan.id(),
                now,
                cause,
                StudyPlanCreatedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(now)
                        .setUserId(plan.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setPlanId(plan.id())
                        .setWeeksTotal(plan.weeksTotal())
                        .setStartDate(plan.startDate())
                        .setExamDate(plan.examDate())
                        .setMinutesPerDay(plan.minutesPerDay())
                        .setTimezone(plan.timezone().getId())
                        .setMockExamWeeks(plan.mockExamWeeks())
                        .build());
    }

    @Override
    public void weekPublished(Week week, Cause cause) {
        Instant now = Objects.requireNonNullElse(week.publishedAt(), week.generatedAt());
        double minutes =
                week.tasks().stream().mapToDouble(WeekTask::estimatedMinutes).sum();
        UUID draftId = UUID.randomUUID();
        publish(
                draftId,
                WEEK_DRAFT_GENERATED,
                "Week",
                week.id(),
                week.userId(),
                subject(week),
                week.generatedAt(),
                cause,
                WeekDraftGeneratedV1.newBuilder()
                        .setEventId(draftId)
                        .setOccurredAt(week.generatedAt())
                        .setUserId(week.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setWeekId(week.id())
                        .setPlanId(week.planId())
                        .setWeekNo(week.weekNo())
                        .setStatus(WeekStatus.PUBLISHED)
                        .setApprovalDeadline(null)
                        .setWeakTaskTypes(week.weakTypes().stream()
                                .map(OutboxPlanningEventPublisherAdapter::type)
                                .toList())
                        .setTaskCount(week.tasks().size())
                        .setEstimatedMinutes(minutes)
                        .build());
        UUID eventId = UUID.randomUUID();
        List<PublishedWeekTaskV1> tasks = new ArrayList<>();
        for (WeekTask task : week.tasks()) {
            tasks.add(PublishedWeekTaskV1.newBuilder()
                    .setWeekTaskId(task.id())
                    .setKind(com.toefly.events.common.WeekTaskKind.valueOf(
                            task.kind().name()))
                    .setWeekSection(section(task.section()))
                    .setTaskType(task.taskType() == null ? null : type(task.taskType()))
                    .setRefId(refUuid(task.refId()))
                    .setLocalDate(week.date(task.dayNo()))
                    .setEstimatedMinutes(task.estimatedMinutes())
                    .setIsRepeat(task.repeat())
                    .build());
        }
        publish(
                eventId,
                WEEK_PUBLISHED,
                "Week",
                week.id(),
                week.userId(),
                subject(week),
                now,
                new Cause(cause.correlationId(), draftId),
                WeekPublishedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(now)
                        .setUserId(week.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(draftId)
                        .setWeekId(week.id())
                        .setPlanId(week.planId())
                        .setWeekNo(week.weekNo())
                        .setDay1(week.day1())
                        .setAutoPublished(false)
                        .setLockedByEntitlement(false)
                        .setTasks(tasks)
                        .build());
    }

    @Override
    public void weekTaskCompleted(Week week, WeekTask task, LocalDate localDate, Cause cause) {
        UUID eventId = UUID.randomUUID();
        Instant now = Objects.requireNonNullElse(task.completedAt(), clock.instant());
        publish(
                eventId,
                WEEK_TASK_COMPLETED,
                "Week",
                week.id(),
                week.userId(),
                subject(week) + "/tasks/" + task.id(),
                now,
                cause,
                WeekTaskCompletedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(now)
                        .setUserId(week.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setWeekId(week.id())
                        .setWeekTaskId(task.id())
                        .setWeekNo(week.weekNo())
                        .setKind(com.toefly.events.common.WeekTaskKind.valueOf(
                                task.kind().name()))
                        .setTaskType(task.taskType() == null ? null : type(task.taskType()))
                        .setWeekSection(section(task.section()))
                        .setEstimatedMinutes(task.estimatedMinutes())
                        .setLocalDate(localDate)
                        .setAttemptId(task.attemptId())
                        .setProgressPercent(week.progressPercent())
                        .build());
    }

    @Override
    public void weekRebalanced(Week week, Rebalance rebalance, Instant occurredAt, Cause cause) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                WEEK_REBALANCED,
                "Week",
                week.id(),
                week.userId(),
                subject(week),
                occurredAt,
                cause,
                WeekRebalancedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(occurredAt)
                        .setUserId(week.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setWeekId(week.id())
                        .setWeekNo(week.weekNo())
                        .setLagDays(rebalance.lagDays())
                        .setSkipped(rebalance.skipped())
                        .setRedistributedTo(rebalance.moves().stream()
                                .map(move -> RedistributedTaskV1.newBuilder()
                                        .setWeekTaskId(move.taskId())
                                        .setLocalDate(move.date())
                                        .build())
                                .toList())
                        .setManual(rebalance.manual())
                        .build());
    }

    @Override
    public void weekCompleted(Week week, Cause cause) {
        UUID eventId = UUID.randomUUID();
        Instant closedAt = Objects.requireNonNull(week.completedAt());
        LocalDate endDate = Objects.requireNonNull(week.closedOn());
        int dayCount = week.dayNo(endDate);
        List<SectionProgressV1> sections = new ArrayList<>();
        for (WeekSection section : WeekSection.values()) {
            List<WeekTask> ofSection = week.tasks().stream()
                    .filter(task -> task.section() == section && task.countsInProgress())
                    .toList();
            if (ofSection.isEmpty()) {
                continue;
            }
            List<WeekTask> done = ofSection.stream()
                    .filter(task -> task.status() == WeekTaskStatus.DONE)
                    .toList();
            sections.add(SectionProgressV1.newBuilder()
                    .setSection(section(section))
                    .setTasksTotal(ofSection.size())
                    .setTasksDone(done.size())
                    .setMinutesPlanned((int) Math.round(minutes(ofSection)))
                    .setMinutesDone((int) Math.round(minutes(done)))
                    .build());
        }
        long counted = week.tasks().stream().filter(WeekTask::countsInProgress).count();
        long doneCount = week.tasks().stream()
                .filter(task -> task.status() == WeekTaskStatus.DONE)
                .count();
        long skipped = week.tasks().stream()
                .filter(task -> task.status() == WeekTaskStatus.SKIPPED)
                .count();
        publish(
                eventId,
                WEEK_COMPLETED,
                "Week",
                week.id(),
                week.userId(),
                subject(week),
                closedAt,
                cause,
                WeekCompletedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(closedAt)
                        .setUserId(week.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setWeekId(week.id())
                        .setPlanId(week.planId())
                        .setWeekNo(week.weekNo())
                        .setStatus(WeekStatus.COMPLETED)
                        .setStartDate(week.day1())
                        .setEndDate(endDate)
                        .setClosedAt(closedAt)
                        .setDayCount(dayCount)
                        .setExtended(dayCount > Week.DAYS)
                        .setFinishedEarly(week.closedBy() == com.toefly.planning.domain.week.WeekCloseReason.EARLY)
                        .setClosedBy(WeekCloseReason.valueOf(
                                Objects.requireNonNull(week.closedBy()).name()))
                        .setProgressPercent(week.progressPercent())
                        .setTasksTotal((int) counted)
                        .setTasksDone((int) doneCount)
                        .setTasksSkipped((int) skipped)
                        .setMockExamWeek(week.mockExam())
                        .setTutorId(null)
                        .setSections(sections)
                        .build());
    }

    private void publish(
            UUID eventId,
            String topic,
            String aggregateType,
            UUID aggregateId,
            UUID userId,
            String subject,
            Instant occurredAt,
            Cause cause,
            SpecificRecord payload) {
        publisher.publish(new IntegrationEvent(
                eventId,
                topic,
                aggregateType,
                aggregateId.toString(),
                userId.toString(),
                subject,
                occurredAt,
                userId,
                cause.correlationId(),
                cause.causationId(),
                payload));
    }

    private static String subject(Week week) {
        return "weeks/" + week.id();
    }

    private static double minutes(List<WeekTask> tasks) {
        return tasks.stream().mapToDouble(WeekTask::estimatedMinutes).sum();
    }

    private static com.toefly.events.common.TaskType type(TaskType type) {
        return com.toefly.events.common.TaskType.valueOf(type.name());
    }

    private static com.toefly.events.common.WeekSection section(WeekSection section) {
        return com.toefly.events.common.WeekSection.valueOf(section.name());
    }

    /** Item sets and units carry a UUID; a resource carries its URL, which the event leaves out. */
    private static @Nullable UUID refUuid(@Nullable String refId) {
        if (refId == null) {
            return null;
        }
        try {
            return UUID.fromString(refId);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }
}
