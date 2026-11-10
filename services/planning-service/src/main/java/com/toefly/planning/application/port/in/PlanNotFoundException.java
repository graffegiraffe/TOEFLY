package com.toefly.planning.application.port.in;

import com.toefly.common.web.ProblemType;

/** The learner has no plan yet (onboarding not finished). */
@ProblemType(value = "not-found", status = 404)
public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException() {
        super("There is no study plan yet.");
    }
}
