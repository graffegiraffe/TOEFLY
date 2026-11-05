package com.toefly.profile.application.port.in;

import com.toefly.profile.domain.profile.ExamPlan;
import com.toefly.profile.domain.profile.OnboardingDraft;
import com.toefly.profile.domain.profile.StudyGoal;
import com.toefly.profile.domain.profile.UserProfile;

public interface SaveOnboardingUseCase {

    /** Saves onboarding steps; with {@code complete} finishes it and publishes onboarding-completed (US-02.04). */
    UserProfile saveOnboarding(SignedInUser user, OnboardingDraft draft, boolean complete);

    /** Changes the goal after the onboarding and publishes the next revision (US-02.05). */
    UserProfile changeGoal(SignedInUser user, StudyGoal goal, ExamPlan exam, int minutesPerDay, long expectedVersion);
}
