package com.toefly.aigateway.application.port.in;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** Every candidate is down, or the local safety models are, so nothing may leave (§2.2.1, ADR-0013). */
@ProblemType(value = "ai-provider-unavailable", status = 503)
public class AiProviderUnavailableException extends RuntimeException implements ProblemProperties, RetryAfter {

    private final long retryAfterSeconds;

    public AiProviderUnavailableException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("retryAfterSeconds", retryAfterSeconds);
    }
}
