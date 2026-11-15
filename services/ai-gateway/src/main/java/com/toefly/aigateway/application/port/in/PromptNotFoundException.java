package com.toefly.aigateway.application.port.in;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** No such prompt, version, or no active version. */
@ProblemType(value = "prompt-not-found", status = 404)
public class PromptNotFoundException extends RuntimeException implements ProblemProperties {

    private final String promptKey;

    public PromptNotFoundException(String promptKey, String message) {
        super(message);
        this.promptKey = promptKey;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("promptKey", promptKey);
    }
}
