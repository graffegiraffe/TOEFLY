package com.toefly.profile.domain.profile;

import com.toefly.common.web.ProblemType;

/** The onboarding is done; goal and schedule change in the settings from now on (AC-02.04.3). */
@ProblemType(value = "conflict", status = 409)
public class OnboardingAlreadyCompletedException extends RuntimeException {

    public OnboardingAlreadyCompletedException() {
        super("The onboarding is already completed. Change the goal in the settings.");
    }
}
