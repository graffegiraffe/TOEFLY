package com.toefly.practice.domain.attempt;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.time.Instant;
import java.util.Map;

/** An EXAM answer arrived after the deadline plus the 2-second grace (INV-ATT-02). */
@ProblemType(value = "attempt-time-expired", status = 409)
public class AttemptTimeExpiredException extends RuntimeException implements ProblemProperties {

    private final Instant deadlineAt;

    public AttemptTimeExpiredException(Instant deadlineAt) {
        super("The time of this exam module is over.");
        this.deadlineAt = deadlineAt;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("deadlineAt", deadlineAt.toString());
    }
}
