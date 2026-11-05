package com.toefly.profile.domain.privacy;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.time.Duration;
import java.util.Map;

@ProblemType(value = "conflict", status = 409)
public class DataExportTooSoonException extends RuntimeException implements ProblemProperties {

    private final Duration retryAfter;

    public DataExportTooSoonException(Duration retryAfter) {
        super("An export was requested less than 24 hours ago.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("retryAfterSeconds", Math.max(1, retryAfter.toSeconds()));
    }
}
