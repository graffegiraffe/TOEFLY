package com.toefly.profile.adapter.out.messaging;

import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.profile.AccountDeletionRequestedV1;
import com.toefly.events.profile.ConsentChangedV1;
import com.toefly.events.profile.OnboardingCompletedV1;
import com.toefly.events.profile.PreferencesUpdatedV1;
import com.toefly.events.profile.ProfileCreatedV1;
import com.toefly.profile.application.port.out.ProfileEventPublisherPort;
import com.toefly.profile.domain.consent.Consent;
import com.toefly.profile.domain.privacy.AccountDeletion;
import com.toefly.profile.domain.profile.Band;
import com.toefly.profile.domain.profile.ExamPlan;
import com.toefly.profile.domain.profile.Preferences;
import com.toefly.profile.domain.profile.Role;
import com.toefly.profile.domain.profile.StudyGoal;
import com.toefly.profile.domain.profile.UserProfile;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Profile events in the canonical topics (docs/00-README.md §6), keyed by {@code userId}. */
@Component
public class OutboxProfileEventPublisherAdapter implements ProfileEventPublisherPort {

    static final String PROFILE_CREATED = "toefly.profile.profile-created.v1";
    static final String ONBOARDING_COMPLETED = "toefly.profile.onboarding-completed.v1";
    static final String PREFERENCES_UPDATED = "toefly.profile.preferences-updated.v1";
    static final String CONSENT_CHANGED = "toefly.profile.consent-changed.v1";
    static final String ACCOUNT_DELETION_REQUESTED = "toefly.profile.account-deletion-requested.v1";

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final IntegrationEventPublisher publisher;

    public OutboxProfileEventPublisherAdapter(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void profileCreated(UserProfile profile, boolean emailVerified) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                PROFILE_CREATED,
                profile.userId(),
                profile.createdAt(),
                ProfileCreatedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(profile.createdAt())
                        .setUserId(profile.userId())
                        .setRoles(roles(profile.roles()))
                        .setEmailVerified(emailVerified)
                        .build());
    }

    @Override
    public void onboardingCompleted(UserProfile profile, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        StudyGoal goal = Objects.requireNonNull(profile.goal());
        ExamPlan exam = profile.exam() == null ? ExamPlan.notBooked() : profile.exam();
        Preferences preferences = profile.preferences();
        publish(
                eventId,
                ONBOARDING_COMPLETED,
                profile.userId(),
                occurredAt,
                OnboardingCompletedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(occurredAt)
                        .setUserId(profile.userId())
                        .setRevision(profile.goalRevision())
                        .setOverallTarget(goal.overallTarget().value())
                        .setReadingTarget(value(goal.readingTarget()))
                        .setListeningTarget(value(goal.listeningTarget()))
                        .setWritingTarget(value(goal.writingTarget()))
                        .setSpeakingTarget(value(goal.speakingTarget()))
                        .setExamBooked(exam.booked())
                        .setExamDate(exam.examDate())
                        .setTimezone(preferences.timezone().getId())
                        .setExplanationLanguage(
                                preferences.explanationLanguage().code())
                        .setMinutesPerDay(preferences.minutesPerDay())
                        .setPreferredStudyTime(preferences.preferredStudyTime().format(HH_MM))
                        .build());
    }

    @Override
    public void preferencesUpdated(UserProfile profile, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        Preferences preferences = profile.preferences();
        publish(
                eventId,
                PREFERENCES_UPDATED,
                profile.userId(),
                occurredAt,
                PreferencesUpdatedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(occurredAt)
                        .setUserId(profile.userId())
                        .setTimezone(preferences.timezone().getId())
                        .setExplanationLanguage(
                                preferences.explanationLanguage().code())
                        .setMinutesPerDay(preferences.minutesPerDay())
                        .setPreferredStudyTime(preferences.preferredStudyTime().format(HH_MM))
                        .build());
    }

    @Override
    public void consentChanged(Consent consent) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                CONSENT_CHANGED,
                consent.userId(),
                consent.recordedAt(),
                ConsentChangedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(consent.recordedAt())
                        .setUserId(consent.userId())
                        .setConsentType(com.toefly.events.common.ConsentType.valueOf(
                                consent.type().name()))
                        .setGranted(consent.granted())
                        .setPolicyVersion(consent.policyVersion())
                        .build());
    }

    @Override
    public void accountDeletionRequested(AccountDeletion deletion, Set<Role> roles) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                ACCOUNT_DELETION_REQUESTED,
                deletion.userId(),
                deletion.requestedAt(),
                AccountDeletionRequestedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(deletion.requestedAt())
                        .setUserId(deletion.userId())
                        .setDeletionId(deletion.id())
                        .setRequestedAt(deletion.requestedAt())
                        .setLegalDeadlineAt(deletion.legalDeadlineAt())
                        .setRoles(roles(roles))
                        .build());
    }

    private void publish(UUID eventId, String topic, UUID userId, Instant occurredAt, SpecificRecord payload) {
        publisher.publish(new IntegrationEvent(
                eventId,
                topic,
                "UserProfile",
                userId.toString(),
                userId.toString(),
                "users/" + userId,
                occurredAt,
                userId,
                null,
                null,
                payload));
    }

    private static List<com.toefly.events.common.Role> roles(Set<Role> roles) {
        return roles.stream()
                .sorted()
                .map(role -> com.toefly.events.common.Role.valueOf(role.name()))
                .toList();
    }

    private static @Nullable Double value(@Nullable Band band) {
        return band == null ? null : band.value();
    }
}
