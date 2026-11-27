package com.toefly.tutoragent.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.assessment.BandEstimateUpdatedV1;
import com.toefly.events.assessment.SectionEstimateV1;
import com.toefly.events.assessment.SubmissionEvaluatedV1;
import com.toefly.events.common.SubmissionStatus;
import com.toefly.events.practice.AttemptScoredV1;
import com.toefly.events.profile.PreferencesUpdatedV1;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase.ScoredAttempt;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase.ScoredItem;
import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * Updates of the learner model by the events of docs/06 §6.2. One listener per topic in this consumer group:
 * onboarding-completed is handled by {@link ProjectionListener}.
 */
@Component
public class LearnerModelListener {

    private final ProcessedEventGuard guard;
    private final LearnerModelUseCase learner;

    public LearnerModelListener(ProcessedEventGuard guard, LearnerModelUseCase learner) {
        this.guard = guard;
        this.learner = learner;
    }

    @ToeflyListener(id = "learner-attempt-scored", topics = "toefly.practice.attempt-scored.v1")
    void onScored(ConsumerRecord<String, AttemptScoredV1> record) {
        AttemptScoredV1 e = record.value();
        if (e == null) {
            return;
        }
        ScoredAttempt attempt = new ScoredAttempt(
                e.getUserId(),
                e.getAttemptId(),
                e.getTaskType().name(),
                e.getScoredAt(),
                e.getItems().stream()
                        .map(i -> new ScoredItem(i.getItemId(), i.getAnswered(), i.getCorrect()))
                        .toList());
        guard.runOnce(
                record,
                "LearnerModelListener.attemptScored",
                () -> learner.attemptScored(attempt, new Cause(e.getCorrelationId(), e.getEventId())));
    }

    @ToeflyListener(id = "learner-submission-evaluated", topics = "toefly.assessment.submission-evaluated.v1")
    void onEvaluated(ConsumerRecord<String, SubmissionEvaluatedV1> record) {
        SubmissionEvaluatedV1 e = record.value();
        if (e == null || e.getStatus() != SubmissionStatus.EVALUATED) {
            return;
        }
        guard.runOnce(
                record,
                "LearnerModelListener.submissionEvaluated",
                () -> learner.submissionEvaluated(
                        e.getUserId(),
                        e.getSubmissionId(),
                        e.getVersionNo(),
                        e.getEvaluatedAt(),
                        new Cause(e.getCorrelationId(), e.getEventId())));
    }

    @ToeflyListener(id = "learner-band-estimate", topics = "toefly.assessment.band-estimate-updated.v1")
    void onBands(ConsumerRecord<String, BandEstimateUpdatedV1> record) {
        BandEstimateUpdatedV1 e = record.value();
        if (e == null) {
            return;
        }
        Map<String, Double> bands = new LinkedHashMap<>();
        for (SectionEstimateV1 s : e.getSections()) {
            if (s.getPointBand() != null) {
                bands.put(s.getSection().name(), s.getPointBand());
            }
        }
        if (e.getOverallPoint() != null) {
            bands.put("OVERALL", e.getOverallPoint());
        }
        guard.runOnce(
                record,
                "LearnerModelListener.bands",
                () -> learner.bandsUpdated(e.getUserId(), bands, new Cause(e.getCorrelationId(), e.getEventId())));
    }

    @ToeflyListener(id = "learner-preferences", topics = "toefly.profile.preferences-updated.v1")
    void onPreferences(ConsumerRecord<String, PreferencesUpdatedV1> record) {
        PreferencesUpdatedV1 e = record.value();
        if (e == null || e.getExplanationLanguage() == null) {
            return;
        }
        guard.runOnce(
                record,
                "LearnerModelListener.preferences",
                () -> learner.languageChanged(
                        e.getUserId(),
                        ExplanationLanguage.of(e.getExplanationLanguage()),
                        new Cause(e.getCorrelationId(), e.getEventId())));
    }
}
