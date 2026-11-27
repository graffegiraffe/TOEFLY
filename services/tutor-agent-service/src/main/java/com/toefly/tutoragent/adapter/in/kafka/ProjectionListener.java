package com.toefly.tutoragent.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.billing.EntitlementsChangedV1;
import com.toefly.events.common.AttemptMode;
import com.toefly.events.planning.WeekRebalancedV1;
import com.toefly.events.practice.AttemptStartedV1;
import com.toefly.events.practice.AttemptSubmittedV1;
import com.toefly.events.practice.TimeTrackedV1;
import com.toefly.events.profile.AccountDeletionRequestedV1;
import com.toefly.events.profile.OnboardingCompletedV1;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.application.port.in.ProjectionUseCase;
import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** Rights, exam sessions, activity and week progress (docs/06 §4.2, §5.10; docs/12, phase 5c, decisions 5 and 7). */
@Component
public class ProjectionListener {

    private final ProcessedEventGuard guard;
    private final ProjectionUseCase projections;
    private final LearnerModelUseCase learner;

    public ProjectionListener(ProcessedEventGuard guard, ProjectionUseCase projections, LearnerModelUseCase learner) {
        this.guard = guard;
        this.projections = projections;
        this.learner = learner;
    }

    @ToeflyListener(id = "projection-entitlements", topics = "toefly.billing.entitlements-changed.v1")
    void onEntitlements(ConsumerRecord<String, EntitlementsChangedV1> record) {
        EntitlementsChangedV1 e = record.value();
        if (e == null) {
            return;
        }
        guard.runOnce(
                record,
                "ProjectionListener.entitlements",
                () -> projections.entitlementsChanged(
                        e.getUserId(),
                        e.getEntitlements().stream().map(Enum::name).toList(),
                        e.getEntitlementsVersion(),
                        e.getChangedAt()));
    }

    @ToeflyListener(id = "projection-attempt-started", topics = "toefly.practice.attempt-started.v1")
    void onStarted(ConsumerRecord<String, AttemptStartedV1> record) {
        AttemptStartedV1 e = record.value();
        if (e == null) {
            return;
        }
        guard.runOnce(record, "ProjectionListener.attemptStarted", () -> {
            projections.active(e.getUserId(), e.getStartedAt());
            if (e.getMode() == AttemptMode.EXAM) {
                projections.examAttemptStarted(e.getUserId(), e.getAttemptId(), e.getStartedAt());
            }
        });
    }

    @ToeflyListener(id = "projection-attempt-submitted", topics = "toefly.practice.attempt-submitted.v1")
    void onSubmitted(ConsumerRecord<String, AttemptSubmittedV1> record) {
        AttemptSubmittedV1 e = record.value();
        if (e == null) {
            return;
        }
        guard.runOnce(record, "ProjectionListener.attemptSubmitted", () -> {
            projections.active(e.getUserId(), e.getSubmittedAt());
            projections.examAttemptEnded(e.getAttemptId());
        });
    }

    @ToeflyListener(id = "projection-time-tracked", topics = "toefly.practice.time-tracked.v1")
    void onTime(ConsumerRecord<String, TimeTrackedV1> record) {
        TimeTrackedV1 e = record.value();
        if (e == null || e.getActiveSeconds() <= 0) {
            return;
        }
        guard.runOnce(
                record, "ProjectionListener.timeTracked", () -> projections.active(e.getUserId(), e.getOccurredAt()));
    }

    @ToeflyListener(id = "projection-onboarding", topics = "toefly.profile.onboarding-completed.v1")
    void onOnboarding(ConsumerRecord<String, OnboardingCompletedV1> record) {
        OnboardingCompletedV1 e = record.value();
        if (e == null) {
            return;
        }
        guard.runOnce(record, "ProjectionListener.onboarding", () -> {
            projections.profileKnown(e.getUserId(), e.getTimezone(), e.getExamDate());
            projections.active(e.getUserId(), e.getOccurredAt());
            learner.goalChanged(
                    e.getUserId(),
                    e.getOverallTarget(),
                    e.getExamDate(),
                    e.getTimezone(),
                    ExplanationLanguage.of(e.getExplanationLanguage()),
                    new Cause(e.getCorrelationId(), e.getEventId()));
        });
    }

    @ToeflyListener(id = "projection-week-rebalanced", topics = "toefly.planning.week-rebalanced.v1")
    void onRebalanced(ConsumerRecord<String, WeekRebalancedV1> record) {
        WeekRebalancedV1 e = record.value();
        if (e == null || e.getManual()) {
            return;
        }
        guard.runOnce(
                record,
                "ProjectionListener.weekRebalanced",
                () -> projections.weekProgress(
                        e.getUserId(), e.getWeekId(), e.getWeekNo(), 0, true, e.getOccurredAt()));
    }

    @ToeflyListener(id = "projection-account-deletion", topics = "toefly.profile.account-deletion-requested.v1")
    void onDeletion(ConsumerRecord<String, AccountDeletionRequestedV1> record) {
        AccountDeletionRequestedV1 e = record.value();
        if (e == null) {
            return;
        }
        guard.runOnce(
                record,
                "ProjectionListener.accountDeletion",
                () -> projections.accountDeletionRequested(e.getUserId()));
    }
}
