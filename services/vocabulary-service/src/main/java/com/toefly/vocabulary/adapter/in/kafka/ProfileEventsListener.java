package com.toefly.vocabulary.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.profile.AccountDeletionRequestedV1;
import com.toefly.events.profile.PreferencesUpdatedV1;
import com.toefly.vocabulary.application.port.in.LearnerLifecycleUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * preferences-updated gives the zone of the learner's day (AC-15.03.1); the erasure removes the dictionary
 * (docs/04 §5.11). Both are idempotent through {@code processed_events}.
 */
@Component
public class ProfileEventsListener {

    private final ProcessedEventGuard guard;
    private final LearnerLifecycleUseCase lifecycle;

    public ProfileEventsListener(ProcessedEventGuard guard, LearnerLifecycleUseCase lifecycle) {
        this.guard = guard;
        this.lifecycle = lifecycle;
    }

    @ToeflyListener(id = "vocabulary-preferences-updated", topics = "toefly.profile.preferences-updated.v1")
    void onPreferences(ConsumerRecord<String, PreferencesUpdatedV1> record) {
        PreferencesUpdatedV1 event = record.value();
        guard.runOnce(
                record,
                "ProfileEventsListener.preferences",
                () -> lifecycle.preferencesUpdated(event.getUserId(), event.getTimezone()));
    }

    @ToeflyListener(id = "vocabulary-account-deletion", topics = "toefly.profile.account-deletion-requested.v1")
    void onDeletion(ConsumerRecord<String, AccountDeletionRequestedV1> record) {
        guard.runOnce(
                record,
                "ProfileEventsListener.deletion",
                () -> lifecycle.accountDeletionRequested(record.value().getUserId()));
    }
}
