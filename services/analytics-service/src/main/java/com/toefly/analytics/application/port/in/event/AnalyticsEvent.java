package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A fact of another service in the form the projections read; the Kafka adapter decodes it from Avro and the journal
 * keeps it as JSON for rebuilds (docs/04 §6.7).
 */
public sealed interface AnalyticsEvent
        permits ProfileCreated,
                OnboardingCompleted,
                PreferencesUpdated,
                StudyPlanCreated,
                WeekPublished,
                WeekTaskCompleted,
                WeekRebalanced,
                WeekCompleted,
                AttemptStarted,
                AttemptSubmitted,
                AttemptScored,
                AbilityUpdated,
                TimeTracked,
                BandEstimateUpdated,
                SubmissionEvaluated,
                UsageRecorded {

    /** The learner the event is about; {@code null} for AI calls without a user. */
    @Nullable
    UUID userId();

    Instant at();
}
