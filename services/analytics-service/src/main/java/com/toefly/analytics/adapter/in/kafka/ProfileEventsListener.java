package com.toefly.analytics.adapter.in.kafka;

import com.toefly.analytics.application.port.in.ForgetLearnerUseCase;
import com.toefly.analytics.application.port.in.RecordEventUseCase;
import com.toefly.analytics.application.port.in.event.EventMeta;
import com.toefly.analytics.application.port.in.event.OnboardingCompleted;
import com.toefly.analytics.application.port.in.event.PreferencesUpdated;
import com.toefly.analytics.application.port.in.event.ProfileCreated;
import com.toefly.analytics.domain.common.Section;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.profile.AccountDeletionRequestedV1;
import com.toefly.events.profile.OnboardingCompletedV1;
import com.toefly.events.profile.PreferencesUpdatedV1;
import com.toefly.events.profile.ProfileCreatedV1;
import java.util.EnumMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Registrations and onboarding feed the funnel; the schedule and goal feed the days and the streak. */
@Component
public class ProfileEventsListener {

    private final ProcessedEventGuard guard;
    private final RecordEventUseCase recorder;
    private final ForgetLearnerUseCase forget;
    private final ConsistencyLag lag;

    public ProfileEventsListener(
            ProcessedEventGuard guard, RecordEventUseCase recorder, ForgetLearnerUseCase forget, ConsistencyLag lag) {
        this.guard = guard;
        this.recorder = recorder;
        this.forget = forget;
        this.lag = lag;
    }

    @ToeflyListener(id = "profile-created", topics = "toefly.profile.profile-created.v1")
    void onCreated(ConsumerRecord<String, ProfileCreatedV1> record) {
        ProfileCreatedV1 e = record.value();
        ProfileCreated event = new ProfileCreated(
                e.getUserId(), e.getRoles().stream().map(Enum::name).toList(), e.getOccurredAt());
        EventMeta meta = new EventMeta(e.getEventId(), record.topic(), e.getOccurredAt(), e.getUserId());
        lag.applied(
                guard.runOnce(record, "ProfileEventsListener.created", () -> recorder.record(meta, event)),
                e.getOccurredAt());
    }

    @ToeflyListener(id = "onboarding-completed", topics = "toefly.profile.onboarding-completed.v1")
    void onOnboarding(ConsumerRecord<String, OnboardingCompletedV1> record) {
        OnboardingCompletedV1 e = record.value();
        Map<Section, Double> targets = new EnumMap<>(Section.class);
        put(targets, Section.READING, e.getReadingTarget());
        put(targets, Section.LISTENING, e.getListeningTarget());
        put(targets, Section.WRITING, e.getWritingTarget());
        put(targets, Section.SPEAKING, e.getSpeakingTarget());
        OnboardingCompleted event = new OnboardingCompleted(
                e.getUserId(),
                e.getTimezone(),
                e.getMinutesPerDay(),
                e.getExamDate(),
                e.getOverallTarget(),
                targets,
                e.getOccurredAt());
        EventMeta meta = new EventMeta(e.getEventId(), record.topic(), e.getOccurredAt(), e.getUserId());
        lag.applied(
                guard.runOnce(record, "ProfileEventsListener.onboarding", () -> recorder.record(meta, event)),
                e.getOccurredAt());
    }

    @ToeflyListener(id = "preferences-updated", topics = "toefly.profile.preferences-updated.v1")
    void onPreferences(ConsumerRecord<String, PreferencesUpdatedV1> record) {
        PreferencesUpdatedV1 e = record.value();
        if (e == null) {
            return; // tombstone of the compacted topic after an account deletion
        }
        PreferencesUpdated event =
                new PreferencesUpdated(e.getUserId(), e.getTimezone(), e.getMinutesPerDay(), e.getOccurredAt());
        EventMeta meta = new EventMeta(e.getEventId(), record.topic(), e.getOccurredAt(), e.getUserId());
        lag.applied(
                guard.runOnce(record, "ProfileEventsListener.preferences", () -> recorder.record(meta, event)),
                e.getOccurredAt());
    }

    /** INV-ANL-05: the learner leaves every read model and the journal. */
    @ToeflyListener(id = "account-deletion-requested", topics = "toefly.profile.account-deletion-requested.v1")
    void onDeletion(ConsumerRecord<String, AccountDeletionRequestedV1> record) {
        AccountDeletionRequestedV1 e = record.value();
        guard.runOnce(record, "ProfileEventsListener.deletion", () -> forget.forget(e.getUserId()));
    }

    private static void put(Map<Section, Double> targets, Section section, @Nullable Double value) {
        if (value != null) {
            targets.put(section, value);
        }
    }
}
