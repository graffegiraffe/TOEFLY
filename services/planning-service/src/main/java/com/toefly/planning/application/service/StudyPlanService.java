package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.BuildStudyPlanUseCase;
import com.toefly.planning.application.port.in.ChangeStudyPlanUseCase;
import com.toefly.planning.application.port.in.OnboardingGoal;
import com.toefly.planning.application.port.in.PlanAlreadyExistsException;
import com.toefly.planning.application.port.in.PlanNotFoundException;
import com.toefly.planning.application.port.in.VersionMismatchException;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.PlanningEventPublisherPort;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.plan.LearnerCalendar;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Week;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The plan up to the exam: built from the onboarding (docs/12, phase 4, decision 1), changed from next week. */
@Service
@Transactional
public class StudyPlanService implements BuildStudyPlanUseCase, ChangeStudyPlanUseCase {

    private final StudyPlanRepository plans;
    private final WeekRepository weeks;
    private final WeekPlanner planner;
    private final PlanningEventPublisherPort events;
    private final Clock clock;

    public StudyPlanService(
            StudyPlanRepository plans,
            WeekRepository weeks,
            WeekPlanner planner,
            PlanningEventPublisherPort events,
            Clock clock) {
        this.plans = plans;
        this.weeks = weeks;
        this.planner = planner;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void onboardingCompleted(OnboardingGoal goal, Cause cause) {
        Instant now = clock.instant();
        var existing = plans.findByUser(goal.userId());
        if (existing.isPresent()) {
            StudyPlan plan = existing.get();
            LocalDate today = LearnerCalendar.today(now, plan.timezone());
            if (plan.changeGoal(
                    goal.revision(), goal.examDate(), goal.minutesPerDay(), goal.timezone(), goal.targets(), today)) {
                plans.update(plan);
            }
            return;
        }
        LocalDate today = LearnerCalendar.today(now, goal.timezone());
        StudyPlan plan = StudyPlan.create(
                goal.userId(),
                goal.examDate(),
                today,
                goal.timezone(),
                goal.minutesPerDay(),
                goal.targets(),
                goal.revision(),
                now);
        start(plan, today, now, cause);
    }

    @Override
    public StudyPlan create(UUID userId, LocalDate examDate, @Nullable Integer weeksCount, int minutesPerDay) {
        if (plans.findByUser(userId).isPresent()) {
            throw new PlanAlreadyExistsException();
        }
        Instant now = clock.instant();
        ZoneId zone = ZoneOffset.UTC;
        LocalDate today = LearnerCalendar.today(now, zone);
        StudyPlan plan = StudyPlan.create(userId, examDate, today, zone, minutesPerDay, Map.of(), 0, now);
        if (weeksCount != null) {
            plan = new StudyPlan(
                    plan.id(),
                    userId,
                    examDate,
                    Math.min(StudyPlan.MAX_WEEKS, Math.max(1, weeksCount)),
                    today,
                    zone,
                    minutesPerDay,
                    1,
                    Map.of(),
                    0,
                    now,
                    0);
        }
        start(plan, today, now, Cause.none());
        return plan;
    }

    private void start(StudyPlan plan, LocalDate today, Instant now, Cause cause) {
        plans.insert(plan);
        Week first = planner.compose(plan, 1, today, now);
        weeks.insert(first);
        events.studyPlanCreated(plan, cause);
        events.weekPublished(first, cause);
    }

    @Override
    public void preferencesUpdated(UUID userId, ZoneId timezone, int minutesPerDay) {
        plans.findByUser(userId).ifPresent(plan -> {
            plan.changePreferences(timezone, minutesPerDay);
            plans.update(plan);
        });
    }

    @Override
    public StudyPlan patch(UUID userId, int ifMatch, @Nullable LocalDate examDate, @Nullable Integer minutesPerDay) {
        StudyPlan plan = plans.findByUser(userId).orElseThrow(PlanNotFoundException::new);
        if (plan.version() != ifMatch) {
            throw new VersionMismatchException(plan.version());
        }
        LocalDate today = LearnerCalendar.today(clock.instant(), plan.timezone());
        plan.changeGoal(
                plan.goalRevision() + 1,
                examDate != null ? examDate : plan.examDate(),
                minutesPerDay != null ? minutesPerDay : plan.minutesPerDay(),
                plan.timezone(),
                plan.targets(),
                today);
        plans.update(plan);
        return plans.findByUser(userId).orElseThrow();
    }
}
