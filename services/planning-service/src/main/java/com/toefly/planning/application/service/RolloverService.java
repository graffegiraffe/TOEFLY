package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.RolloverUseCase;
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
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The start of each new local day of a learner (docs/03 §10.4–10.6, docs/12 phase 4 decision 7): tasks of an
 * abandoned attempt go back to PENDING, the week closes at 70% after day 7 or after day 10, and a week lagging more
 * than 3 days is rebalanced. Days missed while the job did not run are processed one by one.
 */
@Service
public class RolloverService implements RolloverUseCase {

    private static final Logger log = LoggerFactory.getLogger(RolloverService.class);

    private final StudyPlanRepository plans;
    private final WeekRepository weeks;
    private final WeekLifecycle lifecycle;
    private final PlanningEventPublisherPort events;
    private final Transactions transactions;
    private final Clock clock;

    public RolloverService(
            StudyPlanRepository plans,
            WeekRepository weeks,
            WeekLifecycle lifecycle,
            PlanningEventPublisherPort events,
            Transactions transactions,
            Clock clock) {
        this.plans = plans;
        this.weeks = weeks;
        this.lifecycle = lifecycle;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public int rolloverAll() {
        int changed = 0;
        for (UUID planId : weeks.plansWithOpenWeeks()) {
            try {
                if (transactions.retryOnConflict(() -> rollover(planId))) {
                    changed++;
                }
            } catch (RuntimeException failure) {
                log.warn("Rollover of plan {} failed; the next run repeats it", planId, failure);
            }
        }
        return changed;
    }

    boolean rollover(UUID planId) {
        Optional<StudyPlan> found = plans.findById(planId);
        if (found.isEmpty()) {
            return false;
        }
        StudyPlan plan = found.get();
        Instant now = clock.instant();
        LocalDate today = LearnerCalendar.today(now, plan.timezone());
        boolean changed = false;
        Optional<Week> open = openWeek(planId);
        while (open.isPresent()) {
            Week week = open.get();
            LocalDate from = week.lastRolloverDate() != null
                    ? week.lastRolloverDate().plusDays(1)
                    : week.day1().plusDays(1);
            if (from.isAfter(today)) {
                break;
            }
            Optional<Week> next = Optional.empty();
            boolean closed = false;
            for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
                Instant midnight = LearnerCalendar.midnight(date, plan.timezone());
                week.releaseAbandoned(midnight);
                week.rolledOver(date);
                if (week.closeIfDue(date, midnight).isPresent()) {
                    next = lifecycle.closed(plan, week, midnight, Cause.none());
                    closed = true;
                    break;
                }
                week.rebalance(date, midnight, false)
                        .filter(rebalance -> !rebalance.skipped().isEmpty())
                        .ifPresent(rebalance -> events.weekRebalanced(week, rebalance, midnight, Cause.none()));
            }
            if (!closed) {
                weeks.update(week);
            }
            changed = true;
            open = next;
        }
        return changed;
    }

    private Optional<Week> openWeek(UUID planId) {
        return weeks.findByPlan(planId).stream()
                .filter(week -> week.status().open())
                .min(Comparator.comparingInt(Week::weekNo));
    }
}
