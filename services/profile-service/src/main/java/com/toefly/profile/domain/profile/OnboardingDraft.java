package com.toefly.profile.domain.profile;

import java.time.LocalTime;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/** Values of one or more onboarding steps; absent values leave the saved ones untouched (AC-02.04.2). */
public record OnboardingDraft(
        @Nullable StudyGoal goal,
        @Nullable ExamPlan exam,
        @Nullable Integer minutesPerDay,
        @Nullable LocalTime preferredStudyTime,
        @Nullable ZoneId timezone,
        @Nullable ExplanationLanguage explanationLanguage) {

    public static OnboardingDraft empty() {
        return new OnboardingDraft(null, null, null, null, null, null);
    }
}
