package com.toefly.planning.application.port.in;

import com.toefly.common.web.ProblemType;

@ProblemType(value = "plan-already-exists", status = 409)
public class PlanAlreadyExistsException extends RuntimeException {

    public PlanAlreadyExistsException() {
        super("A study plan exists already; change it with PATCH /study-plans/current.");
    }
}
