package com.toefly.aigateway.application.port.in;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** No candidate of the chain fits its quota within the wait (§2.2.1). */
@ProblemType(value = "ai-quota-exhausted", status = 429)
public class AiQuotaExhaustedException extends RuntimeException implements ProblemProperties, RetryAfter {

    private final long retryAfterSeconds;

    public AiQuotaExhaustedException(long retryAfterSeconds) {
        super("The free-tier quota of every model of this route is used up; retry in " + retryAfterSeconds + " s.");
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
