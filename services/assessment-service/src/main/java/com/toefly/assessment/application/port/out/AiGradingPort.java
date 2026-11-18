package com.toefly.assessment.application.port.out;

import com.toefly.assessment.domain.grading.PassOutput;

/** ai-gateway for the grader; every failure that is not a valid answer is {@link AiUnavailableException}. */
public interface AiGradingPort {

    PassOutput grade(GradingRequest request);
}
