package com.toefly.practice.domain.attempt;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** Answers change only while the attempt is IN_PROGRESS (INV-ATT-02). */
@ProblemType(value = "attempt-already-submitted", status = 409)
public class AttemptAlreadySubmittedException extends RuntimeException implements ProblemProperties {

    private final AttemptStatus status;

    public AttemptAlreadySubmittedException(AttemptStatus status) {
        super("The attempt is " + status + " and can no longer change.");
        this.status = status;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("attemptStatus", status.name());
    }
}
