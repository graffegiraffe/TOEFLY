package com.toefly.aigateway.application.port.in;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.List;
import java.util.Map;

/** The answer failed its JSON Schema after all repeats on all candidates. */
@ProblemType(value = "ai-output-invalid", status = 422)
public class AiOutputInvalidException extends RuntimeException implements ProblemProperties {

    private final List<String> violations;

    public AiOutputInvalidException(List<String> violations) {
        super("The model answer does not match the output schema of the prompt.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("violations", violations);
    }
}
