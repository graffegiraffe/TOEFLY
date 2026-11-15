package com.toefly.aigateway.application.service;

import com.toefly.aigateway.domain.call.TokenUsage;
import java.util.List;

/** An answer that came back but failed its JSON Schema; the candidate is asked again with the violations. */
final class InvalidOutput extends RuntimeException {

    private final List<String> violations;
    private final String answer;
    private final TokenUsage usage;

    InvalidOutput(List<String> violations, String answer, TokenUsage usage) {
        super("output schema violated: " + violations);
        this.violations = List.copyOf(violations);
        this.answer = answer;
        this.usage = usage;
    }

    List<String> violations() {
        return violations;
    }

    String answer() {
        return answer;
    }

    TokenUsage usage() {
        return usage;
    }
}
