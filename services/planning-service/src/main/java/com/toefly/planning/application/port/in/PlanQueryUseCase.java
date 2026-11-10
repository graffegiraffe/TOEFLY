package com.toefly.planning.application.port.in;

import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekTask;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface PlanQueryUseCase {

    Optional<StudyPlan> plan(UUID userId);

    /** All weeks of the plan, composed or not (AC-04.01.3). */
    List<WeekSlot> weeks(UUID userId);

    /** A week the learner may see; empty for drafts and other users (AC-04.02.4). */
    Optional<WeekView> week(UUID userId, UUID weekId);

    Optional<WeekView> weekById(UUID weekId);

    Optional<WeekView> currentWeek(UUID userId);

    Today today(UUID userId, @Nullable LocalDate date);

    Optional<UserData> export(UUID userId);

    /** A week with the plan it belongs to and the learner's today. */
    record WeekView(Week week, StudyPlan plan, LocalDate today) {}

    /** A slot of the gallery: the composed week or only its number and dates. */
    record WeekSlot(
            int weekNo,
            LocalDate startDate,
            LocalDate endDate,
            boolean mockExam,
            @Nullable Week week) {}

    record Today(
            LocalDate date,
            @Nullable Week week,
            List<WeekTask> overdue,
            List<WeekTask> today,
            List<WeekTask> getAhead,
            int minutesPerDay) {}

    record UserData(StudyPlan plan, List<WeekView> weeks) {}
}
