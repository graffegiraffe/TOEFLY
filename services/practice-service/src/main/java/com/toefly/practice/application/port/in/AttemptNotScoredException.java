package com.toefly.practice.application.port.in;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import com.toefly.practice.domain.attempt.AttemptStatus;
import java.util.Map;

/** A review exists only for a scored attempt. */
@ProblemType(value = "conflict", status = 409)
public class AttemptNotScoredException extends RuntimeException implements ProblemProperties {

    private final AttemptStatus status;

    public AttemptNotScoredException(AttemptStatus status) {
        super("The attempt is " + status + "; its review opens after submission.");
        this.status = status;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("attemptStatus", status.name());
    }
}
