package com.toefly.planning.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.profile.AccountDeletionRequestedV1;
import com.toefly.events.profile.OnboardingCompletedV1;
import com.toefly.events.profile.PreferencesUpdatedV1;
import com.toefly.planning.application.port.in.BuildStudyPlanUseCase;
import com.toefly.planning.application.port.in.ChangeStudyPlanUseCase;
import com.toefly.planning.application.port.in.OnboardingGoal;
import com.toefly.planning.application.port.in.SyncLearnerUseCase;
import com.toefly.planning.application.port.out.Cause;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** The onboarding builds the plan (docs/12, phase 4, decision 1); preferences and erasure follow. */
@Component
public class ProfileEventsListener {

    private final ProcessedEventGuard guard;
    private final BuildStudyPlanUseCase plans;
    private final ChangeStudyPlanUseCase changes;
    private final SyncLearnerUseCase sync;

    public ProfileEventsListener(
            ProcessedEventGuard guard,
            BuildStudyPlanUseCase plans,
            ChangeStudyPlanUseCase changes,
            SyncLearnerUseCase sync) {
        this.guard = guard;
        this.plans = plans;
        this.changes = changes;
        this.sync = sync;
    }

    @ToeflyListener(id = "onboarding-completed", topics = "toefly.profile.onboarding-completed.v1")
    void onOnboarding(ConsumerRecord<String, OnboardingCompletedV1> record) {
        OnboardingCompletedV1 event = record.value();
        Map<String, Double> targets = new HashMap<>();
        targets.put("OVERALL", event.getOverallTarget());
        put(targets, "READING", event.getReadingTarget());
        put(targets, "LISTENING", event.getListeningTarget());
        put(targets, "WRITING", event.getWritingTarget());
        put(targets, "SPEAKING", event.getSpeakingTarget());
        guard.runOnce(
                record,
                "ProfileEventsListener.onboarding",
                () -> plans.onboardingCompleted(
                        new OnboardingGoal(
                                event.getUserId(),
                                event.getRevision(),
                                event.getExamBooked() ? event.getExamDate() : null,
                                ZoneId.of(event.getTimezone()),
                                event.getMinutesPerDay(),
                                targets),
                        new Cause(event.getCorrelationId(), event.getEventId())));
    }

    @ToeflyListener(id = "preferences-updated", topics = "toefly.profile.preferences-updated.v1")
    void onPreferences(ConsumerRecord<String, PreferencesUpdatedV1> record) {
        if (record.value() == null) {
            return; // tombstone after an erasure
        }
        PreferencesUpdatedV1 event = record.value();
        guard.runOnce(
                record,
                "ProfileEventsListener.preferences",
                () -> changes.preferencesUpdated(
                        event.getUserId(), ZoneId.of(event.getTimezone()), event.getMinutesPerDay()));
    }

    @ToeflyListener(id = "account-deletion-requested", topics = "toefly.profile.account-deletion-requested.v1")
    void onDeletion(ConsumerRecord<String, AccountDeletionRequestedV1> record) {
        guard.runOnce(
                record,
                "ProfileEventsListener.deletion",
                () -> sync.deleteUser(record.value().getUserId()));
    }

    private static void put(Map<String, Double> targets, String section, Double value) {
        if (value != null) {
            targets.put(section, value);
        }
    }
}
