package com.toefly.planning.application.port.in;

import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.domain.plan.StudyPlan;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The plan up to the exam and its first week (US-04.01). */
public interface BuildStudyPlanUseCase {

    /** Builds the plan and Week 1 on the first onboarding; a later revision changes the plan from next week. */
    void onboardingCompleted(OnboardingGoal goal, Cause cause);

    /** {@code POST /study-plans}: a plan without the onboarding event; 409 when one exists. */
    StudyPlan create(UUID userId, LocalDate examDate, @Nullable Integer weeksCount, int minutesPerDay);
}
