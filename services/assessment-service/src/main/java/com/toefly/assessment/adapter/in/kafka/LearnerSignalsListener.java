package com.toefly.assessment.adapter.in.kafka;

import com.toefly.assessment.application.port.in.BandEstimateUseCase;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.domain.estimate.Section;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.common.AttemptMode;
import com.toefly.events.common.TaskType;
import com.toefly.events.practice.AbilityUpdatedV1;
import com.toefly.events.practice.AttemptScoredV1;
import com.toefly.events.practice.ItemResultV1;
import com.toefly.events.profile.OnboardingCompletedV1;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * Inputs of the band estimate (docs/03-domain-model.md §8.2–8.6): θ of Reading and Listening, results of Build a
 * Sentence for Writing, and the date of onboarding for the 7-day rule.
 */
@Component
public class LearnerSignalsListener {

    private final ProcessedEventGuard guard;
    private final BandEstimateUseCase estimates;

    public LearnerSignalsListener(ProcessedEventGuard guard, BandEstimateUseCase estimates) {
        this.guard = guard;
        this.estimates = estimates;
    }

    @ToeflyListener(id = "ability-updated", topics = "toefly.practice.ability-updated.v1")
    void onAbility(ConsumerRecord<String, AbilityUpdatedV1> record) {
        AbilityUpdatedV1 event = record.value();
        if (event == null) {
            return;
        }
        String section = event.getSection().name();
        if (!section.equals("READING") && !section.equals("LISTENING")) {
            return;
        }
        guard.runOnce(
                record,
                "LearnerSignalsListener.ability",
                () -> estimates.abilityUpdated(
                        event.getUserId(),
                        Section.valueOf(section),
                        event.getThetaAfter(),
                        event.getStandardError(),
                        event.getN(),
                        event.getOccurredAt(),
                        new Cause(event.getCorrelationId(), event.getEventId())));
    }

    @ToeflyListener(id = "attempt-scored", topics = "toefly.practice.attempt-scored.v1")
    void onScored(ConsumerRecord<String, AttemptScoredV1> record) {
        AttemptScoredV1 event = record.value();
        if (event == null
                || event.getTaskType() != TaskType.BUILD_A_SENTENCE
                || event.getMode() != AttemptMode.PRACTICE) {
            return;
        }
        List<Boolean> correct = event.getItems().stream()
                .filter(ItemResultV1::getAnswered)
                .map(ItemResultV1::getCorrect)
                .toList();
        if (correct.isEmpty()) {
            return;
        }
        guard.runOnce(
                record,
                "LearnerSignalsListener.buildSentence",
                () -> estimates.buildSentenceScored(
                        event.getUserId(),
                        event.getAttemptId(),
                        correct,
                        event.getScoredAt(),
                        new Cause(event.getCorrelationId(), event.getEventId())));
    }

    @ToeflyListener(id = "onboarding-completed", topics = "toefly.profile.onboarding-completed.v1")
    void onOnboarding(ConsumerRecord<String, OnboardingCompletedV1> record) {
        OnboardingCompletedV1 event = record.value();
        if (event == null) {
            return;
        }
        guard.runOnce(
                record,
                "LearnerSignalsListener.onboarding",
                () -> estimates.onboardingCompleted(
                        event.getUserId(),
                        event.getOccurredAt(),
                        new Cause(event.getCorrelationId(), event.getEventId())));
    }
}
