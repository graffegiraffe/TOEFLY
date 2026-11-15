package com.toefly.aigateway.application.port.in;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** The rendered prompt exceeds the input budget of its version (§2.5.1); the worker must shorten its input. */
@ProblemType(value = "prompt-too-large", status = 422)
public class PromptTooLargeException extends RuntimeException implements ProblemProperties {

    private final int estimatedTokens;
    private final int maxInputTokens;

    public PromptTooLargeException(int estimatedTokens, int maxInputTokens) {
        super("The prompt takes about " + estimatedTokens + " tokens; the limit is " + maxInputTokens + ".");
        this.estimatedTokens = estimatedTokens;
        this.maxInputTokens = maxInputTokens;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("estimatedTokens", estimatedTokens, "maxInputTokens", maxInputTokens);
    }
}
