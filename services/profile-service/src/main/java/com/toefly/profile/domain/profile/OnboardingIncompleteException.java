package com.toefly.profile.domain.profile;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;
import java.util.Set;

/** Completing the onboarding needs every step (AC-02.04.1). */
@ProblemType(value = "business-rule-violated", status = 422)
public class OnboardingIncompleteException extends RuntimeException implements ProblemProperties {

    private final Set<OnboardingStep> missing;

    public OnboardingIncompleteException(Set<OnboardingStep> missing) {
        super("Fill in every onboarding step first.");
        this.missing = Set.copyOf(missing);
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of(
                "rule",
                "onboarding-incomplete",
                "missingSteps",
                missing.stream().map(Enum::name).sorted().toList());
    }
}
