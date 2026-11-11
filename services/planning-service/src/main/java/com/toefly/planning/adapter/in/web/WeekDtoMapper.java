package com.toefly.planning.adapter.in.web;

import com.toefly.planning.adapter.in.web.dto.DayPlan;
import com.toefly.planning.adapter.in.web.dto.Provenance;
import com.toefly.planning.adapter.in.web.dto.RebalanceInfo;
import com.toefly.planning.adapter.in.web.dto.TodayTask;
import com.toefly.planning.adapter.in.web.dto.TodayVocabularyReview;
import com.toefly.planning.adapter.in.web.dto.WeekSectionBlock;
import com.toefly.planning.adapter.in.web.dto.WeekSummary;
import com.toefly.planning.adapter.in.web.dto.WeekSummaryFinalScore;
import com.toefly.planning.application.port.in.PlanQueryUseCase.Today;
import com.toefly.planning.application.port.in.PlanQueryUseCase.WeekSlot;
import com.toefly.planning.application.port.in.PlanQueryUseCase.WeekView;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.TaskOrigin;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekStatus;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import com.toefly.planning.domain.week.WeekTaskStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Domain objects to the DTOs of planning-service.yaml. */
final class WeekDtoMapper {

    private WeekDtoMapper() {}

    static com.toefly.planning.adapter.in.web.dto.StudyPlan plan(StudyPlan plan, LocalDate today) {
        LinkedHashSet<Integer> days = new LinkedHashSet<>(List.of(1, 2, 3, 4, 5, 6, 7));
        return new com.toefly.planning.adapter.in.web.dto.StudyPlan()
                .planId(plan.id())
                .userId(plan.userId())
                .examDate(plan.examDate())
                .timezone(plan.timezone().getId())
                .weeksCount(plan.weeksTotal())
                .minutesPerDay(plan.minutesPerDay())
                .studyDays(days)
                .startDate(plan.startDate())
                .currentWeekNo(plan.currentWeekNo())
                .daysToExam(plan.daysToExam(today))
                .createdAt(plan.createdAt().atOffset(ZoneOffset.UTC))
                .version(plan.version());
    }

    static WeekSummary summary(WeekSlot slot, LocalDate today) {
        Week week = slot.week();
        WeekSummary summary = new WeekSummary()
                .weekNo(slot.weekNo())
                .startDate(slot.startDate())
                .endDate(slot.endDate())
                .mockExam(slot.mockExam())
                .locked(false);
        if (week == null) {
            return summary.weekId(new UUID(0, slot.weekNo()))
                    .status(com.toefly.planning.adapter.in.web.dto.WeekStatus.DRAFT)
                    .progressPercent(null);
        }
        summary.weekId(week.id())
                .status(status(week.status()))
                .progressPercent(BigDecimal.valueOf(week.progressPercent()));
        if (week.status() == WeekStatus.COMPLETED) {
            summary.finalScore(new WeekSummaryFinalScore().progressPercent(BigDecimal.valueOf(week.progressPercent())));
        }
        return summary;
    }

    static com.toefly.planning.adapter.in.web.dto.Week week(WeekView view) {
        Week week = view.week();
        LocalDate today = view.today();
        int dayCount = week.dayCount(today);
        int currentDay = week.dayNo(today);
        List<WeekSectionBlock> sections = new ArrayList<>();
        for (WeekSection section : WeekSection.values()) {
            List<com.toefly.planning.adapter.in.web.dto.WeekTask> tasks = week.tasks().stream()
                    .filter(task -> task.section() == section)
                    .map(task -> task(week, task))
                    .toList();
            if (!tasks.isEmpty()) {
                sections.add(new WeekSectionBlock()
                        .section(com.toefly.planning.adapter.in.web.dto.WeekSection.valueOf(section.name()))
                        .tasks(tasks));
            }
        }
        List<DayPlan> days = new ArrayList<>();
        for (int day = 1; day <= dayCount; day++) {
            final int dayNo = day;
            List<WeekTask> ofDay =
                    week.tasks().stream().filter(task -> task.dayNo() == dayNo).toList();
            days.add(new DayPlan()
                    .dayIndex(day)
                    .date(week.date(day))
                    .estimatedMinutes((int) Math.round(ofDay.stream()
                            .mapToDouble(WeekTask::estimatedMinutes)
                            .sum()))
                    .taskIds(ofDay.stream().map(WeekTask::id).toList()));
        }
        RebalanceInfo rebalance = new RebalanceInfo()
                .lastRebalancedAt(
                        week.rebalancedAt() == null ? null : week.rebalancedAt().atOffset(ZoneOffset.UTC))
                .nextAllowedAt(
                        week.nextRebalanceAllowedAt() == null
                                ? null
                                : week.nextRebalanceAllowedAt().atOffset(ZoneOffset.UTC))
                .excludedTaskIds(week.rebalanceSkipped());
        return new com.toefly.planning.adapter.in.web.dto.Week()
                .weekId(week.id())
                .planId(week.planId())
                .userId(week.userId())
                .weekNo(week.weekNo())
                .status(status(week.status()))
                .startDate(week.day1())
                .endDate(week.closedOn() != null ? week.closedOn() : week.date(dayCount))
                .dayCount(dayCount)
                .currentDayIndex(week.status().open() && currentDay >= 1 && currentDay <= dayCount ? currentDay : null)
                .progressPercent(BigDecimal.valueOf(week.progressPercent()))
                .mockExam(week.mockExam())
                .rationale(week.rationale())
                .autoPublished(false)
                .pendingSlots(0)
                .rebalance(rebalance)
                .sections(sections)
                .days(days)
                .version(week.version());
    }

    static com.toefly.planning.adapter.in.web.dto.WeekTask task(Week week, WeekTask task) {
        boolean unit = task.kind() == WeekTaskKind.GRAMMAR_UNIT || task.kind() == WeekTaskKind.VOCABULARY_UNIT;
        return new com.toefly.planning.adapter.in.web.dto.WeekTask()
                .taskId(task.id())
                .weekId(week.id())
                .kind(com.toefly.planning.adapter.in.web.dto.WeekTaskKind.valueOf(
                        task.kind().name()))
                .section(com.toefly.planning.adapter.in.web.dto.WeekSection.valueOf(
                        task.section().name()))
                .taskType(
                        task.taskType() == null
                                ? null
                                : com.toefly.planning.adapter.in.web.dto.TaskType.valueOf(
                                        task.taskType().name()))
                .dayIndex(task.dayNo())
                .ordinal(task.sortOrder())
                .status(com.toefly.planning.adapter.in.web.dto.WeekTaskStatus.valueOf(
                        task.status().name()))
                .skippedReason(
                        task.skippedReason() == null
                                ? null
                                : com.toefly.planning.adapter.in.web.dto.WeekTaskSkipReason.valueOf(
                                        task.skippedReason().name()))
                .estimatedMinutes((int) Math.round(task.estimatedMinutes()))
                .title(task.title())
                .provenance(provenance(task))
                .legacy(task.legacy())
                .itemSetId(task.kind() == WeekTaskKind.ITEM_SET ? uuid(task.refId()) : null)
                .unitId(unit ? uuid(task.refId()) : null)
                .resourceUrl(
                        task.kind() == WeekTaskKind.EXTERNAL_RESOURCE && task.refId() != null ? task.refId() : null)
                .rationale(
                        task.rationale() == null || task.rationale().length() <= 500
                                ? task.rationale()
                                : task.rationale().substring(0, 500))
                .lastAttemptId(task.attemptId())
                .completedAt(
                        task.completedAt() == null ? null : task.completedAt().atOffset(ZoneOffset.UTC));
    }

    static com.toefly.planning.adapter.in.web.dto.Today today(Today today) {
        Week week = today.week();
        var dto = new com.toefly.planning.adapter.in.web.dto.Today()
                .date(today.date())
                .vocabularyReview(new TodayVocabularyReview().dueCount(0).taskId(null))
                .waitingForTutor(false);
        if (week == null) {
            return dto.overdue(List.of())
                    .today(List.of())
                    .estimatedMinutes(0)
                    .doneMinutes(0)
                    .goalReached(false);
        }
        int dayNo = week.dayNo(today.date());
        double planned = minutes(today.today()) + minutes(today.overdue());
        double done = week.tasks().stream()
                .filter(task ->
                        task.status() == WeekTaskStatus.DONE && today.date().equals(task.completedOn()))
                .mapToDouble(WeekTask::estimatedMinutes)
                .sum();
        return dto.weekId(week.id())
                .weekNo(week.weekNo())
                .dayIndex(dayNo >= 1 && dayNo <= Week.MAX_DAYS ? dayNo : null)
                .overdue(today.overdue().stream()
                        .map(task -> new TodayTask().task(task(week, task)).overdueDays(dayNo - task.dayNo()))
                        .toList())
                .today(today.today().stream()
                        .map(task -> new TodayTask().task(task(week, task)).overdueDays(0))
                        .toList())
                .getAhead(today.getAhead().stream()
                        .map(task -> new TodayTask().task(task(week, task)).overdueDays(0))
                        .toList())
                .estimatedMinutes((int) Math.round(planned))
                .doneMinutes((int) Math.round(done))
                .goalReached(done >= today.minutesPerDay());
    }

    private static double minutes(List<WeekTask> tasks) {
        return tasks.stream().mapToDouble(WeekTask::estimatedMinutes).sum();
    }

    private static com.toefly.planning.adapter.in.web.dto.WeekStatus status(WeekStatus status) {
        return com.toefly.planning.adapter.in.web.dto.WeekStatus.valueOf(status.name());
    }

    /** "From book", "AI-generated", "From your tutor" (AC-04.02.2); every set of the catalog is from a book. */
    private static @Nullable Provenance provenance(WeekTask task) {
        if (task.origin() == TaskOrigin.TUTOR) {
            return Provenance.TUTOR_CREATED;
        }
        return switch (task.kind()) {
            case ITEM_SET, GRAMMAR_UNIT, VOCABULARY_UNIT -> Provenance.EXTRACTED;
            default -> null;
        };
    }

    private static @Nullable UUID uuid(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }
}
