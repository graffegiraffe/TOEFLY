package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.in.BandEstimateUseCase;
import com.toefly.assessment.application.port.in.Caller;
import com.toefly.assessment.application.port.out.AssessmentEventPublisherPort;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.application.port.out.EstimateInputsRepository;
import com.toefly.assessment.application.port.out.SubmissionRepository;
import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.estimate.EstimateInputs;
import com.toefly.assessment.domain.estimate.Section;
import com.toefly.assessment.domain.estimate.WeeklyEstimate;
import com.toefly.assessment.domain.submission.TaskType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Recomputes the estimate on {@code ability-updated}, Build a Sentence results, onboarding and evaluated first
 * versions, and publishes {@code band-estimate-updated} when it changed (AC-13.01.2, INV-EST-04).
 */
@Service
class BandEstimateService implements BandEstimateUseCase {

    static final int WRITING_WINDOW = 5;
    static final int HISTORY_WEEKS = 52;

    private final EstimateInputsRepository inputs;
    private final SubmissionRepository submissions;
    private final AssessmentEventPublisherPort events;
    private final Access access;
    private final Transactions transactions;
    private final Clock clock;

    BandEstimateService(
            EstimateInputsRepository inputs,
            SubmissionRepository submissions,
            AssessmentEventPublisherPort events,
            Access access,
            Transactions transactions,
            Clock clock) {
        this.inputs = inputs;
        this.submissions = submissions;
        this.events = events;
        this.access = access;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public BandEstimate current(Caller caller, @Nullable UUID studentId) {
        UUID userId = access.student(caller, studentId);
        return inputs.current(userId).orElseGet(() -> compute(userId));
    }

    @Override
    public List<WeeklyEstimate> history(Caller caller, @Nullable UUID studentId) {
        return inputs.history(access.student(caller, studentId), HISTORY_WEEKS);
    }

    @Override
    public void abilityUpdated(
            UUID userId, Section section, double theta, double standardError, int n, Instant at, Cause cause) {
        inputs.saveAbility(userId, section, theta, standardError, n, at);
        recompute(userId, cause);
    }

    @Override
    public void buildSentenceScored(UUID userId, UUID attemptId, List<Boolean> correct, Instant at, Cause cause) {
        inputs.addBuildSentenceItems(userId, attemptId, correct, at);
        recompute(userId, cause);
    }

    @Override
    public void onboardingCompleted(UUID userId, Instant at, Cause cause) {
        inputs.onboardingCompleted(userId, at);
        recompute(userId, cause);
    }

    /** Runs inside the caller's transaction; publishes only a changed estimate. */
    void recompute(UUID userId, Cause cause) {
        BandEstimate next = compute(userId);
        Optional<BandEstimate> previous = inputs.current(userId);
        if (previous.isPresent() && same(previous.get(), next)) {
            return;
        }
        LocalDate week = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        inputs.saveEstimate(userId, next, week);
        events.bandEstimateUpdated(userId, next, cause);
    }

    /** Recompute outside a transaction (used by tests and admin tools). */
    void recomputeNow(UUID userId) {
        transactions.once(() -> {
            recompute(userId, Cause.none());
            return userId;
        });
    }

    private BandEstimate compute(UUID userId) {
        Instant now = clock.instant();
        EstimateInputs base = inputs.load(userId, now);
        EstimateInputs full = new EstimateInputs(
                base.onboardingCompletedAt(),
                base.abilities(),
                base.buildSentenceItems(),
                submissions.firstVersionScores(userId, TaskType.WRITE_EMAIL, WRITING_WINDOW),
                submissions.firstVersionScores(userId, TaskType.WRITE_ACADEMIC_DISCUSSION, WRITING_WINDOW),
                base.asOf());
        return BandEstimate.compute(full, now);
    }

    private static boolean same(BandEstimate a, BandEstimate b) {
        return Objects.equals(a.overall(), b.overall())
                && a.sections().size() == b.sections().size()
                && a.sections().stream().allMatch(s -> {
                    var other = b.section(s.section());
                    return s.ready() == other.ready()
                            && Objects.equals(s.point(), other.point())
                            && Objects.equals(s.low(), other.low())
                            && Objects.equals(s.high(), other.high())
                            && s.n() == other.n();
                });
    }
}
