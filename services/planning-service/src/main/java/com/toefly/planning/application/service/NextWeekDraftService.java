package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.DraftNextWeekUseCase;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.PlanningEventPublisherPort;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.application.service.WeekGenerator.Generated;
import com.toefly.planning.application.service.WeekPlanner.Prepared;
import com.toefly.planning.domain.composition.WeekFocus;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Week;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The week after a closed week is built once by the rules when the week closes (docs/03 §10.6–10.7), so the learner
 * never waits for the review. When {@code weekly-review-generated} arrives and that next week is still untouched
 * (published, no task started, drafted by the rules), WeekGenerator drafts it again from the focus of the review and
 * the learner model; the week keeps its id and dates and is published again. A week that has started, or that a
 * review drafted already, stays as it is.
 */
@Service
public class NextWeekDraftService implements DraftNextWeekUseCase {

    private static final Logger log = LoggerFactory.getLogger(NextWeekDraftService.class);

    private final StudyPlanRepository plans;
    private final WeekRepository weeks;
    private final WeekPlanner planner;
    private final WeekGenerator generator;
    private final PlanningEventPublisherPort events;
    private final Clock clock;

    public NextWeekDraftService(
            StudyPlanRepository plans,
            WeekRepository weeks,
            WeekPlanner planner,
            WeekGenerator generator,
            PlanningEventPublisherPort events,
            Clock clock) {
        this.plans = plans;
        this.weeks = weeks;
        this.planner = planner;
        this.generator = generator;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void weeklyReviewGenerated(WeeklyReview review, Cause cause, Predicate<Runnable> commitOnce) {
        Optional<StudyPlan> plan = plans.findByUser(review.userId());
        Optional<Week> next = plan.flatMap(found -> nextWeek(found, review));
        if (plan.isEmpty() || next.isEmpty() || !next.get().redraftable()) {
            log.info(
                    "Weekly review {} of week {}: the next week is missing, has started or was drafted already",
                    review.reviewId(),
                    review.weekNo());
            commitOnce.test(() -> {});
            return;
        }
        Week week = next.get();
        Instant now = clock.instant();
        WeekFocus focus = WeekFocus.of(review.nextWeekFocus(), planner.model(review.userId()));
        Prepared prepared = planner.prepare(plan.get(), week, focus, now);
        Generated generated =
                generator.generate(week, prepared, focus, review, WeekPlanner.rationale(prepared.measured(), focus));
        commitOnce.test(() -> {
            Week fresh = weeks.findById(week.id()).orElseThrow();
            if (!fresh.redraftable()) {
                log.info("Week {} started while it was drafted; it stays as it is", fresh.id());
                return;
            }
            fresh.redraft(generated.tasks(), generated.laidOut(), generated.rationale(), generated.info(), now);
            weeks.update(fresh);
            events.weekPublished(fresh, cause);
        });
    }

    private Optional<Week> nextWeek(StudyPlan plan, WeeklyReview review) {
        return weeks.findByPlan(plan.id()).stream()
                .filter(week -> week.weekNo() == review.weekNo() + 1)
                .findFirst();
    }
}
