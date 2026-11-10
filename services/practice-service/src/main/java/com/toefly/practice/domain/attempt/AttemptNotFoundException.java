package com.toefly.practice.domain.attempt;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;
import java.util.UUID;

/** The attempt does not exist or belongs to someone else; existence is not disclosed. */
@ProblemType(value = "not-found", status = 404)
public class AttemptNotFoundException extends RuntimeException implements ProblemProperties {

    public AttemptNotFoundException(UUID attemptId) {
        super("Attempt " + attemptId + " was not found.");
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("resourceType", "Attempt");
    }
}
