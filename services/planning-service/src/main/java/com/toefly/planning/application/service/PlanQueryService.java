package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.PlanQueryUseCase;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.plan.LearnerCalendar;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlanQueryService implements PlanQueryUseCase {

    static final int GET_AHEAD = 2;

    private final StudyPlanRepository plans;
    private final WeekRepository weeks;
    private final Clock clock;

    public PlanQueryService(StudyPlanRepository plans, WeekRepository weeks, Clock clock) {
        this.plans = plans;
        this.weeks = weeks;
        this.clock = clock;
    }

    @Override
    public Optional<StudyPlan> plan(UUID userId) {
        return plans.findByUser(userId);
    }

    @Override
    public List<WeekSlot> weeks(UUID userId) {
        Optional<StudyPlan> found = plans.findByUser(userId);
        if (found.isEmpty()) {
            return List.of();
        }
        StudyPlan plan = found.get();
        Map<Integer, Week> composed =
                weeks.findByPlan(plan.id()).stream().collect(Collectors.toMap(Week::weekNo, Function.identity()));
        List<WeekSlot> slots = new ArrayList<>();
        LocalDate start = plan.startDate();
        for (int weekNo = 1; weekNo <= plan.weeksTotal(); weekNo++) {
            Week week = composed.get(weekNo);
            if (week != null) {
                start = week.day1();
            }
            LocalDate end = week != null && week.closedOn() != null ? week.closedOn() : start.plusDays(6);
            boolean visible = week != null && week.status().visibleToStudent();
            slots.add(new WeekSlot(weekNo, start, end, plan.mockExamWeek(weekNo), visible ? week : null));
            start = end.plusDays(1);
        }
        return slots;
    }

    @Override
    public Optional<WeekView> week(UUID userId, UUID weekId) {
        return weeks.findById(weekId)
                .filter(week -> week.userId().equals(userId) && week.status().visibleToStudent())
                .flatMap(this::view);
    }

    @Override
    public Optional<WeekView> weekById(UUID weekId) {
        return weeks.findById(weekId).flatMap(this::view);
    }

    @Override
    public Optional<WeekView> currentWeek(UUID userId) {
        return plans.findByUser(userId).flatMap(plan -> {
            LocalDate today = LearnerCalendar.today(clock.instant(), plan.timezone());
            return current(plan, today).map(week -> new WeekView(week, plan, today));
        });
    }

    @Override
    public Today today(UUID userId, @Nullable LocalDate date) {
        Optional<StudyPlan> found = plans.findByUser(userId);
        if (found.isEmpty()) {
            LocalDate day = date != null ? date : LocalDate.now(clock);
            return new Today(day, null, List.of(), List.of(), List.of(), 0);
        }
        StudyPlan plan = found.get();
        LocalDate day = date != null ? date : LearnerCalendar.today(clock.instant(), plan.timezone());
        Optional<Week> current = current(plan, day);
        if (current.isEmpty()) {
            return new Today(day, null, List.of(), List.of(), List.of(), plan.minutesPerDay());
        }
        Week week = current.get();
        List<WeekTask> overdue = week.overdue(day);
        List<WeekTask> planned = week.planned(day);
        List<WeekTask> ahead = List.of();
        if (planned.stream().allMatch(task -> task.status() == WeekTaskStatus.DONE) && overdue.isEmpty()) {
            ahead = week.planned(day.plusDays(1)).stream()
                    .filter(task -> task.status().open())
                    .limit(GET_AHEAD)
                    .toList();
        }
        return new Today(day, week, overdue, planned, ahead, week.minutesPerDay());
    }

    @Override
    public Optional<UserData> export(UUID userId) {
        return plans.findByUser(userId).map(plan -> {
            LocalDate today = LearnerCalendar.today(clock.instant(), plan.timezone());
            List<WeekView> views = weeks.findByPlan(plan.id()).stream()
                    .map(week -> new WeekView(week, plan, today))
                    .toList();
            return new UserData(plan, views);
        });
    }

    /** The open week that has started: in progress, or published with day 1 not after {@code today} (§10.4). */
    private Optional<Week> current(StudyPlan plan, LocalDate today) {
        return weeks.findByPlan(plan.id()).stream()
                .filter(week -> week.status().open() && !week.day1().isAfter(today))
                .max(Comparator.comparingInt(Week::weekNo));
    }

    private Optional<WeekView> view(Week week) {
        return plans.findById(week.planId())
                .map(plan -> new WeekView(week, plan, LearnerCalendar.today(clock.instant(), plan.timezone())));
    }
}
